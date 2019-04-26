/*
 * Copyright (C) 2005-2019 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package com.manydesigns.portofino.pages;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.manydesigns.elements.ElementsThreadLocals;
import com.manydesigns.portofino.pageactions.*;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.xml.bind.*;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A collection of methods that operate on pages and related objects.
 *
 * @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
 * @author Angelo Lupo          - angelo.lupo@manydesigns.com
 * @author Giampiero Granatella - giampiero.granatella@manydesigns.com
 * @author Alessio Stalla       - alessio.stalla@manydesigns.com
 */
public class PageLogic {
    public static final String copyright =
            "Copyright (C) 2005-2019 ManyDesigns srl";

    public static final Logger logger = LoggerFactory.getLogger(PageLogic.class);

    protected static final JAXBContext pagesJaxbContext;

    static {
        try {
            pagesJaxbContext = JAXBContext.newInstance(Page.class.getPackage().getName());
        } catch (JAXBException e) {
            throw new Error("Can't instantiate pages jaxb context", e);
        }
    }

    /**
     * Persists a page to the file system.
     * @param pageInstance the live PageInstance containing the Page to save. 
     * @return the file where the page was saved.
     * @throws Exception in case the save fails.
     */
    public static FileObject savePage(PageInstance pageInstance) throws Exception {
        return savePage(pageInstance.getDirectory(), pageInstance.getPage());
    }

    /**
     * Persists a page to the file system.
     * @param directory the directory where to save the page.xml file.
     * @param page the page to save.
     * @return the file where the page was saved.
     * @throws Exception in case the save fails.
     */
    public static FileObject savePage(FileObject directory, Page page) throws Exception {
        FileObject pageFile = getPageFile(directory);
        Marshaller marshaller = pagesJaxbContext.createMarshaller();
        marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        if(!pageFile.exists()) {
            pageFile.createFile();
        }
        try(OutputStream outputStream = pageFile.getContent().getOutputStream()) {
            marshaller.marshal(page, outputStream);
            pageCache.invalidate(pageFile);
        }
        return pageFile;
    }

    //Cache configuration properties
    public static final String PAGE_CACHE_SIZE = "page.cache.size";
    public static final String PAGE_CACHE_CHECK_FREQUENCY = "page.cache.check.frequency";
    public static final String CONFIGURATION_CACHE_SIZE = "configuration.cache.size";
    public static final String CONFIGURATION_CACHE_CHECK_FREQUENCY = "configuration.cache.check.frequency";

    public static void init(Configuration portofinoConfiguration) {
        int maxSize, refreshCheckFrequency;
        maxSize = portofinoConfiguration.getInt(PAGE_CACHE_SIZE, 1000);
        refreshCheckFrequency =
                portofinoConfiguration.getInt(PAGE_CACHE_CHECK_FREQUENCY, 5);
        initPageCache(maxSize, refreshCheckFrequency);
        maxSize = portofinoConfiguration.getInt(CONFIGURATION_CACHE_SIZE, 1000);
        refreshCheckFrequency =
                portofinoConfiguration.getInt(CONFIGURATION_CACHE_CHECK_FREQUENCY, 5);
        initConfigurationCache(maxSize, refreshCheckFrequency);
    }

    protected static class FileCacheEntry<T> {
        public final T object;
        public final long lastModified;
        public final boolean error;

        public FileCacheEntry(T object, long lastModified, boolean error) {
            this.object = object;
            this.lastModified = lastModified;
            this.error = error;
        }
    }

    protected static class ConfigurationCacheEntry extends FileCacheEntry<Object> {
        public final Class<?> configurationClass;

        public ConfigurationCacheEntry(
                Object configuration, Class<?> configurationClass, long lastModified,
                boolean error) {
            super(configuration, lastModified, error);
            this.configurationClass = configurationClass;
        }
    }

    //NB il reload delle cache è _asincrono_ rispetto alla get, è quindi possibile che una get ritorni
    //un valore vecchio anche nel caso in cui sia appena stato rilevato un errore nel reload (es. ho scritto
    //caratteri invalidi all'inizio dell'xml).

    protected static LoadingCache<FileObject, FileCacheEntry<Page>> pageCache;

    public static void initPageCache(int maxSize, int refreshCheckFrequency) {
        pageCache =
                CacheBuilder.newBuilder()
                        .maximumSize(maxSize)
                        .refreshAfterWrite(refreshCheckFrequency, TimeUnit.SECONDS)
                        .build(new CacheLoader<FileObject, FileCacheEntry<Page>>() {

                            @Override
                            public FileCacheEntry<Page> load(FileObject key) throws Exception {
                                return new FileCacheEntry<Page>(loadPage(key), key.getContent().getLastModifiedTime(), false);
                            }

                            @Override
                            public ListenableFuture<FileCacheEntry<Page>> reload(
                                    final FileObject key, FileCacheEntry<Page> oldValue)
                                    throws Exception {
                                if(!key.exists()) {
                                    //Se la pagina non esiste più, registro questo fatto nella cache;
                                    //a questo livello non è un errore, sarà il metodo getPage() a gestire
                                    //la entry problematica.
                                    return Futures.immediateFuture(
                                            new FileCacheEntry<Page>(null, 0, true));
                                } else if (key.getContent().getLastModifiedTime() > oldValue.lastModified) {
                                    /*return ListenableFutureTask.create(new Callable<PageCacheEntry>() {
                                        public PageCacheEntry call() throws Exception {
                                            return doLoad(key);
                                        }
                                    });*/
                                    //TODO async?
                                    try {
                                        Page page = loadPage(key);
                                        return Futures.immediateFuture(
                                                new FileCacheEntry<>(page, key.getContent().getLastModifiedTime(), false));
                                    } catch (Throwable t) {
                                        logger.error(
                                                "Could not reload cached page from " + key.getName().getPath() +
                                                ", removing from cache", t);
                                        return Futures.immediateFuture(
                                                new FileCacheEntry<>(null, key.getContent().getLastModifiedTime(), true));
                                    }
                                } else {
                                    return Futures.immediateFuture(oldValue);
                                }
                            }

                        });
    }

    protected static LoadingCache<FileObject, ConfigurationCacheEntry> configurationCache;

    public static void initConfigurationCache(int maxSize, int refreshCheckFrequency) {
        configurationCache =
                CacheBuilder.newBuilder()
                        .maximumSize(maxSize)
                        .refreshAfterWrite(refreshCheckFrequency, TimeUnit.SECONDS)
                        .build(new CacheLoader<FileObject, ConfigurationCacheEntry>() {

                            @Override
                            public ConfigurationCacheEntry load(FileObject key) throws Exception {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public ListenableFuture<ConfigurationCacheEntry> reload(
                                    final FileObject key, ConfigurationCacheEntry oldValue)
                                    throws Exception {
                                if(!key.exists()) {
                                    //Se la conf. non esiste più, la marco come errata;
                                    //il metodo getConfiguration provvederà a rimuoverla (contrariamente
                                    //alla page, non è possibile lasciare l'oggetto in stato errato nella
                                    //cache perché in generale potrebbero mancare le informazioni per ricaricarlo
                                    //correttamente... TODO da verificare meglio!!!)
                                    return Futures.immediateFuture(
                                            new ConfigurationCacheEntry(null, null, 0, true));
                                } else if (key.getContent().getLastModifiedTime() > oldValue.lastModified) {
                                    //TODO se oldValue.error non dovrei ricaricare (informazioni incomplete) - ?
                                    //TODO async?
                                    try {
                                        Object newConf = loadConfiguration(
                                                key, oldValue.configurationClass);
                                        return Futures.immediateFuture(
                                                new ConfigurationCacheEntry(
                                                        newConf, newConf.getClass(), key.getContent().getLastModifiedTime(),
                                                        false));
                                    } catch (Throwable t) {
                                        logger.error(
                                                "Could not reload cached configuration from " + key.getName().getPath() +
                                                ", removing from cache", t);
                                        return Futures.immediateFuture(
                                            new ConfigurationCacheEntry(null, null, 0, true));
                                    }
                                } else {
                                    return Futures.immediateFuture(oldValue);
                                }
                            }

                        });
    }

    public static void clearConfigurationCache() {
        configurationCache.invalidateAll();
    }

    /**
     * Clears the cache from entries whose class matches exactly with the one passed as a parameter.
     * @param configurationClass the class of the entries to remove.
     */
    public static void clearConfigurationCache(Class configurationClass) {
        Set<Map.Entry<FileObject, ConfigurationCacheEntry>> entries = configurationCache.asMap().entrySet();
        List<FileObject> keysToInvalidate = new ArrayList<>();
        for(Map.Entry<FileObject, ConfigurationCacheEntry> entry : entries) {
            if(entry.getValue().configurationClass == configurationClass) {
                keysToInvalidate.add(entry.getKey());
            }
        }
        configurationCache.invalidateAll(keysToInvalidate);
    }

    protected static FileObject getPageFile(FileObject directory) throws FileSystemException {
        return directory.resolveFile("page.xml");
    }

    public static Page loadPage(FileObject key) throws Exception {
        try(InputStream fileInputStream = key.getContent().getInputStream()) {
            Page page = loadPage(fileInputStream);
            page.init();
            return page;
        }
    }

    public static Page loadPage(InputStream inputStream) throws JAXBException {
        Unmarshaller unmarshaller = pagesJaxbContext.createUnmarshaller();
        return (Page) unmarshaller.unmarshal(inputStream);
    }

    public static Page getPage(FileObject directory) throws PageNotActiveException {
        FileObject pageFile;
        try {
            pageFile = getPageFile(directory);
            FileCacheEntry<Page> entry = pageCache.get(pageFile);
            if(!entry.error) {
                return entry.object;
            } else {
                throw new PageNotActiveException(directory.getName().getPath());
            }
        } catch (Exception e) {
            throw new PageNotActiveException(directory.getName().getPath(), e);
        }
    }

    public static FileObject saveConfiguration(FileObject directory, Object configuration) throws Exception {
        String configurationPackage = configuration.getClass().getPackage().getName();
        JAXBContext jaxbContext = JAXBContext.newInstance(configurationPackage);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        FileObject configurationFile = directory.resolveFile("configuration.xml");
        if(!configurationFile.exists()) {
            configurationFile.createFile();
        }
        try(OutputStream outputStream = configurationFile.getContent().getOutputStream()) {
            marshaller.marshal(configuration, outputStream);
            configurationCache.invalidate(configurationFile);
        }
        return configurationFile;
    }

    public static <T> T getConfiguration(FileObject configurationFile, Class<? extends T> configurationClass)
            throws Exception {
        if (configurationClass == null) {
            return null;
        }
        ConfigurationCacheEntry entry = configurationCache.getIfPresent(configurationFile);
        if(entry == null || !configurationClass.isInstance(entry.object) || entry.error) {
            if(entry != null && entry.error) {
                logger.warn("Cached configuration for {} is in error state, forcing a reload",
                            configurationFile.getName().getPath());
            } else if(entry != null && !configurationClass.isInstance(entry.object)) {
                logger.warn("Cached configuration for {} is an instance of the wrong class, forcing a reload",
                            configurationFile.getName().getPath());
            }
            T configuration = loadConfiguration(configurationFile, configurationClass);
            entry = new ConfigurationCacheEntry(
                    configuration, configurationClass, configurationFile.getContent().getLastModifiedTime(), false);
            configurationCache.put(configurationFile, entry);
        }
        return (T) entry.object;
    }

    public static <T> T loadConfiguration(
            FileObject configurationFile, Class<? extends T> configurationClass) throws Exception {
        if (configurationClass == null) {
            return null;
        }
        try(InputStream inputStream = configurationFile.getContent().getInputStream()) {
            return loadConfiguration(inputStream, configurationClass);
        }
    }

    public static <T> T loadConfiguration
            (InputStream inputStream, Class<? extends T> configurationClass)
            throws Exception {
        if (configurationClass == null) {
            return null;
        }
        Object configuration;
        String configurationPackage = configurationClass.getPackage().getName();
        JAXBContext jaxbContext = JAXBContext.newInstance(configurationPackage);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        configuration = unmarshaller.unmarshal(new StreamSource(inputStream), configurationClass);
        if(configuration instanceof JAXBElement) {
            configuration = ((JAXBElement) configuration).getValue();
        }
        if (!configurationClass.isInstance(configuration)) {
            logger.error("Invalid configuration: expected " + configurationClass + ", got " + configuration);
            return null;
        }
        WebApplicationContext webApplicationContext =
                WebApplicationContextUtils.getRequiredWebApplicationContext(ElementsThreadLocals.getServletContext());
        webApplicationContext.getAutowireCapableBeanFactory().autowireBean(configuration);
        if(configuration instanceof PageActionConfiguration) {
            ((PageActionConfiguration) configuration).init();
        }
        return (T) configuration;
    }

    public static void configurePageAction(PageAction pageAction, PageInstance pageInstance) {
        if(pageInstance.getConfiguration() != null) {
            logger.debug("Page instance {} is already configured");
            return;
        }
        FileObject configurationFile;
        try {
            configurationFile = pageInstance.getDirectory().getChild("configuration.xml");
        } catch (FileSystemException e) {
            throw new RuntimeException(e);
        }
        Class<?> configurationClass = PageActionLogic.getConfigurationClass(pageAction.getClass());
        try {
            Object configuration = getConfiguration(configurationFile, configurationClass);
            pageInstance.setConfiguration(configuration);
        } catch (Throwable t) {
            logger.error("Couldn't load configuration from " + configurationFile.getName().getPath(), t);
        }
        pageAction.setPageInstance(pageInstance);
    }

}
