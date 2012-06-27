/*
 * Copyright (C) 2005-2012 ManyDesigns srl.  All rights reserved.
 * http://www.manydesigns.com/
 *
 * Unless you have purchased a commercial license agreement from ManyDesigns srl,
 * the following license terms apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.manydesigns.portofino.pageactions;

import com.manydesigns.elements.ElementsThreadLocals;
import com.manydesigns.elements.forms.Form;
import com.manydesigns.elements.forms.FormBuilder;
import com.manydesigns.elements.messages.SessionMessages;
import com.manydesigns.elements.options.DefaultSelectionProvider;
import com.manydesigns.elements.options.SelectionProvider;
import com.manydesigns.elements.util.ElementsFileUtils;
import com.manydesigns.portofino.ApplicationAttributes;
import com.manydesigns.portofino.PortofinoProperties;
import com.manydesigns.portofino.RequestAttributes;
import com.manydesigns.portofino.application.Application;
import com.manydesigns.portofino.buttons.annotations.Button;
import com.manydesigns.portofino.di.Inject;
import com.manydesigns.portofino.dispatcher.*;
import com.manydesigns.portofino.model.Model;
import com.manydesigns.portofino.navigation.ResultSetNavigation;
import com.manydesigns.portofino.pages.*;
import com.manydesigns.portofino.scripting.ScriptingUtil;
import com.manydesigns.portofino.security.AccessLevel;
import com.manydesigns.portofino.security.RequiresPermissions;
import com.manydesigns.portofino.stripes.ModelActionResolver;
import groovy.lang.GroovyObject;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.controller.StripesConstants;
import net.sourceforge.stripes.controller.StripesFilter;
import org.apache.commons.collections.MultiHashMap;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.util.*;

/**
* @author Paolo Predonzani     - paolo.predonzani@manydesigns.com
* @author Angelo Lupo          - angelo.lupo@manydesigns.com
* @author Giampiero Granatella - giampiero.granatella@manydesigns.com
* @author Alessio Stalla       - alessio.stalla@manydesigns.com
*/
@RequiresPermissions(level = AccessLevel.VIEW)
public abstract class AbstractPageAction extends AbstractActionBean implements PageAction {
    public static final String copyright =
        "Copyright (c) 2005-2012, ManyDesigns srl";

    public static final String DEFAULT_LAYOUT_CONTAINER = "default";
    public static final String[][] PAGE_CONFIGURATION_FIELDS =
            {{"id", "navigationRoot", "description", "template", "detailTemplate", "applyTemplateRecursively"}};
    public static final String[][] PAGE_CONFIGURATION_FIELDS_NO_DETAIL =
            {{"id", "navigationRoot", "description", "template", "applyTemplateRecursively"}};
    public static final String PAGE_PORTLET_NOT_CONFIGURED = "/layouts/portlet-not-configured.jsp";
    public static final String PORTOFINO_PORTLET_EXCEPTION = "portofino.portlet.exception";

    public static final String CONF_FORM_PREFIX = "config";

    //--------------------------------------------------------------------------
    // Properties
    //--------------------------------------------------------------------------

    //@Inject(RequestAttributes.DISPATCHER)
    //public Dispatcher dispatcher;

    public PageInstance pageInstance;

    @Inject(RequestAttributes.APPLICATION)
    public Application application;

    @Inject(RequestAttributes.MODEL)
    public Model model;

    @Inject(ApplicationAttributes.PORTOFINO_CONFIGURATION)
    public Configuration portofinoConfiguration;

    //--------------------------------------------------------------------------
    // UI
    //--------------------------------------------------------------------------

    public final MultiMap portlets = new MultiHashMap();
    public String returnToParentTarget;
    public final Map<String, String> returnToParentParams = new HashMap<String, String>();
    protected boolean embedded;

    //--------------------------------------------------------------------------
    // Navigation
    //--------------------------------------------------------------------------

    protected ResultSetNavigation resultSetNavigation;
    public String cancelReturnUrl;

    //**************************************************************************
    // Scripting
    //**************************************************************************

    protected String script;

    //**************************************************************************
    // Page configuration
    //**************************************************************************

    public String title;
    public Form pageConfigurationForm;

    //**************************************************************************
    // Logging
    //**************************************************************************

    public static final Logger logger =
            LoggerFactory.getLogger(AbstractPageAction.class);

    public boolean isEmbedded() {
        return embedded;
    }

    /*protected void dereferencePageInstance() {
        if(pageInstance != null) {
            pageInstance = pageInstance.dereference();
        }
    }*/

    public Resolution prepare(PageInstance pageInstance, ActionBeanContext context) {
        this.pageInstance = pageInstance;
        embedded = context.getRequest().getAttribute(StripesConstants.REQ_ATTR_INCLUDE_PATH) != null;
        return null;
    }

    public String getDescription() {
        return pageInstance.getName();
    }

    public void setupReturnToParentTarget() {
        PageInstance[] pageInstancePath =
                Dispatcher.getDispatchForRequest(context.getRequest()).getPageInstancePath();
        boolean hasPrevious = getPage().getActualNavigationRoot() == NavigationRoot.INHERIT;
        hasPrevious = hasPrevious && pageInstancePath.length > 1;
        if(hasPrevious) {
            Page parentPage = pageInstancePath[pageInstancePath.length - 2].getPage();
            hasPrevious = parentPage.getActualNavigationRoot() != NavigationRoot.GHOST_ROOT;
        }
        returnToParentTarget = null;
        if (hasPrevious) {
            int previousPos = pageInstancePath.length - 2;
            PageInstance previousPageInstance = pageInstancePath[previousPos];
            //Page previousPage = previousPageInstance.getPage();

            //TODO ripristinare
            /*if(!previousPage.isShowInNavigation()) {
                return;
            }*/

            PageAction actionBean = previousPageInstance.getActionBean();
            if(actionBean != null) {
                returnToParentTarget = actionBean.getDescription();
            }
        }
    }

    //--------------------------------------------------------------------------
    // Admin methods
    //--------------------------------------------------------------------------

    protected boolean saveConfiguration(Object configuration) {
        try {
            File confFile = DispatcherLogic.saveConfiguration(pageInstance.getDirectory(), configuration);
            logger.info("Configuration saved to " + confFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            logger.error("Couldn't save configuration", e);
            SessionMessages.addErrorMessage("error saving conf");
            return false;
        }
    }

    //--------------------------------------------------------------------------
    // Getters/Setters
    //--------------------------------------------------------------------------

    public Dispatch getDispatch() {
        return Dispatcher.getDispatchForRequest(context.getRequest());
    }

    public String getReturnToParentTarget() {
        return returnToParentTarget;
    }

    public Map<String, String> getReturnToParentParams() {
        return returnToParentParams;
    }

    public MultiMap getPortlets() {
        return portlets;
    }

    public boolean isMultipartRequest() {
        return false;
    }

    public ResultSetNavigation getResultSetNavigation() {
        return resultSetNavigation;
    }

    public void setResultSetNavigation(ResultSetNavigation resultSetNavigation) {
        this.resultSetNavigation = resultSetNavigation;
    }

    public Form getPageConfigurationForm() {
        return pageConfigurationForm;
    }

    public void setPageConfigurationForm(Form pageConfigurationForm) {
        this.pageConfigurationForm = pageConfigurationForm;
    }

    protected void setupPortlets(PageInstance pageInstance, String myself) {
        Layout layout = pageInstance.getLayout();
        if(layout == null) {
            PortletInstance myPortletInstance = new PortletInstance("p", 0, myself);
            portlets.put(DEFAULT_LAYOUT_CONTAINER, myPortletInstance);
            return;
        }
        Self self = layout.getSelf();
        int myOrder = self.getActualOrder();
        PortletInstance myPortletInstance = new PortletInstance("p", myOrder, myself);
        String layoutContainer = self.getContainer();
        if (layoutContainer == null) {
            layoutContainer = DEFAULT_LAYOUT_CONTAINER;
        }
        portlets.put(layoutContainer, myPortletInstance);
        for(ChildPage page : layout.getChildPages()) {
            String layoutContainerInParent = page.getContainer();
            if(layoutContainerInParent != null) {
                String newPath = getDispatch().getOriginalPath() + "/" + page.getName();
                PortletInstance portletInstance =
                        new PortletInstance(
                                "c" + page.getName(),
                                page.getActualOrder(),
                                newPath);

                portlets.put(layoutContainerInParent, portletInstance);
            }
        }
        for(Object entryObj : portlets.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObj;
            List portletContainer = (List) entry.getValue();
            Collections.sort(portletContainer);
        }
    }

    protected Resolution forwardToPortletPage(String pageJsp) {
        setupPortlets(pageInstance, pageJsp);
        HttpServletRequest request = context.getRequest();
        request.setAttribute("cancelReturnUrl", getCancelReturnUrl());
        return new ForwardResolution("/layouts/normal.jsp");
    }

    public String getPageTemplate() {
        Layout layout = getPageInstance().getLayout();
        return getPageTemplate(layout);
    }

    public String getPageTemplate(Layout layout) {
        String template = layout.getTemplate();
        if(StringUtils.isBlank(template)) {
            return getDefaultPageTemplate();
        }

        try {
            Object skin = context.getRequest().getAttribute(RequestAttributes.SKIN);
            String templateRealPath = "/skins/" + skin + template;
            if(context.getServletContext().getResource(templateRealPath) == null) {
                logger.warn("Template file {} does not exist, using default", templateRealPath);
                return getDefaultPageTemplate();
            }
        } catch (MalformedURLException e) {
            logger.error("Malformed template path", e);
            return getDefaultPageTemplate();
        }
        return template;
    }

    protected String getDefaultPageTemplate() {
        return "/templates/default";
    }

    @Button(list = "configuration", key = "commons.cancel", order = 99)
    public Resolution cancel() {
        return new RedirectResolution(getCancelReturnUrl(), false);
    }

    public PageInstance getPageInstance() {
        return pageInstance;
    }

    public Page getPage() {
        return getPageInstance().getPage();
    }

    public void setPageInstance(PageInstance pageInstance) {
        this.pageInstance = pageInstance;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public String getCancelReturnUrl() {
        if (!StringUtils.isEmpty(cancelReturnUrl)) {
            return cancelReturnUrl;
        } else {
            String url = (String) context.getRequest().getAttribute("cancelReturnUrl");
            if(!StringUtils.isEmpty(url)) {
                return url;
            } else {
                return getDefaultCancelReturnUrl();
            }
        }
    }

    protected String getDefaultCancelReturnUrl() {
        return getDispatch().getAbsoluteOriginalPath();
    }

    public void setCancelReturnUrl(String cancelReturnUrl) {
        this.cancelReturnUrl = cancelReturnUrl;
    }

    //--------------------------------------------------------------------------
    // Page configuration
    //--------------------------------------------------------------------------

    protected void prepareConfigurationForms() {
        Page page = pageInstance.getPage();

        PageInstance parent = pageInstance.getParent();
        assert parent != null;

        FormBuilder formBuilder = new FormBuilder(EditPage.class)
                .configPrefix(CONF_FORM_PREFIX)
                .configFields(PageActionLogic.supportsDetail(getClass()) ?
                              PAGE_CONFIGURATION_FIELDS :
                              PAGE_CONFIGURATION_FIELDS_NO_DETAIL)
                .configFieldSetNames("Page");

        SelectionProvider layoutSelectionProvider = createTemplateSelectionProvider();
        formBuilder.configSelectionProvider(layoutSelectionProvider, "template");
        SelectionProvider detailLayoutSelectionProvider = createTemplateSelectionProvider();
        formBuilder.configSelectionProvider(detailLayoutSelectionProvider, "detailTemplate");

        DefaultSelectionProvider navRootSelectionProvider = new DefaultSelectionProvider("navigationRoot");
        String label = getMessage("com.manydesigns.portofino.pageactions.EditPage.navigationRoot.inherit");
        navRootSelectionProvider.appendRow(NavigationRoot.INHERIT, label, true);
        label = getMessage("com.manydesigns.portofino.pageactions.EditPage.navigationRoot.root");
        navRootSelectionProvider.appendRow(NavigationRoot.ROOT, label, true);
        label = getMessage("com.manydesigns.portofino.pageactions.EditPage.navigationRoot.ghost_root");
        navRootSelectionProvider.appendRow(NavigationRoot.GHOST_ROOT, label, true);
        formBuilder.configSelectionProvider(navRootSelectionProvider, "navigationRoot");

        pageConfigurationForm = formBuilder.build();
        EditPage edit = new EditPage();
        edit.id = page.getId();
        edit.description = page.getDescription();
        edit.navigationRoot = page.getActualNavigationRoot();
        edit.template = getPageTemplate(page.getLayout());
        edit.detailTemplate = getPageTemplate(page.getDetailLayout());
        pageConfigurationForm.readFromObject(edit);
        title = page.getTitle();

        if(script == null) {
            prepareScript();
        }
    }

    protected SelectionProvider createTemplateSelectionProvider() {
        String warRealPath =
                portofinoConfiguration.getString(
                        PortofinoProperties.WAR_REAL_PATH);
        File skinsDir = new File(warRealPath, "skins");
        String skin = context.getRequest().getAttribute(RequestAttributes.SKIN) + "";
        File skinDir = new File(skinsDir, skin);
        File layoutsDir = new File(skinDir, "templates");
        DefaultSelectionProvider selectionProvider = new DefaultSelectionProvider("template");
        if(layoutsDir.isDirectory()) {
            File[] files = layoutsDir.listFiles();
            for(File file : files) {
                if(file.isDirectory()) {
                    String path = "/" + ElementsFileUtils.getRelativePath(skinDir, file, "/");
                    String name = file.getName();
                    selectionProvider.appendRow(path, name, true);
                }
            }
        }
        return selectionProvider;
    }

    protected void readPageConfigurationFromRequest() {
        pageConfigurationForm.readFromRequest(context.getRequest());
        title = context.getRequest().getParameter("title");
    }

    protected boolean validatePageConfiguration() {
        boolean valid = true;
        title = StringUtils.trimToNull(title);
        if (title == null) {
            SessionMessages.addErrorMessage(getMessage("commons.configuration.titleEmpty"));
            valid = false;
        }

        valid = pageConfigurationForm.validate() && valid;

        return valid;
    }

    protected boolean updatePageConfiguration() {
        EditPage edit = new EditPage();
        pageConfigurationForm.writeToObject(edit);
        Page page = pageInstance.getPage();
        page.setTitle(title);
        page.setDescription(edit.description);
        page.setNavigationRoot(edit.navigationRoot.name());
        page.getLayout().setTemplate(edit.template);
        page.getDetailLayout().setTemplate(edit.detailTemplate);
        try {
            File pageFile = DispatcherLogic.savePage(pageInstance.getDirectory(), page);
            logger.info("Page saved to " + pageFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Couldn't save page", e);
            return false; //TODO handle return value + script + session msg
        }
        if(edit.applyTemplateRecursively) {
            FileFilter filter = new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.isDirectory();
                }
            };
            updateTemplate(pageInstance.getDirectory(), filter, edit);
        }
        updateScript();
        return true;
    }

    protected void updateTemplate(File directory, FileFilter filter, EditPage edit) {
        File[] children = directory.listFiles(filter);
        for(File child : children) {
            if(!child.getName().equals(PageInstance.DETAIL)) {
                try {
                    Page page = DispatcherLogic.loadPage(child);
                    page.getLayout().setTemplate(edit.template);
                    page.getDetailLayout().setTemplate(edit.detailTemplate);
                    DispatcherLogic.savePage(child, page);
                } catch (Exception e) {
                    logger.warn("Could not set template of " + child.getAbsolutePath(), e);
                }
            }
            updateTemplate(child, filter, edit);
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    //--------------------------------------------------------------------------
    // Scripting
    //--------------------------------------------------------------------------

    protected void prepareScript() {
        String pageId = pageInstance.getPage().getId();
        File file = ScriptingUtil.getGroovyScriptFile(pageInstance.getDirectory(), "action");
        FileReader fr = null;
        try {
            fr = new FileReader(file);
            script = IOUtils.toString(fr);
        } catch (Exception e) {
            logger.warn("Couldn't load script for page " + pageId, e);
        } finally {
            IOUtils.closeQuietly(fr);
        }
    }

    protected void updateScript() {
        File directory = pageInstance.getDirectory();
        File groovyScriptFile = ScriptingUtil.getGroovyScriptFile(directory, "action");
        FileWriter fw = null;
        try {
            fw = new FileWriter(groovyScriptFile);
            fw.write(script);
            fw.flush();
            Class<?> scriptClass = DispatcherLogic.getActionClass(application, directory, false);
            if(scriptClass == null) {
                SessionMessages.addErrorMessage(getMessage("script.class.invalid"));
            }
            if(this instanceof GroovyObject) {
                //Attempt to remove old instance of custom action bean
                //not guaranteed to work
                try {
                    ModelActionResolver actionResolver =
                            (ModelActionResolver) StripesFilter.getConfiguration().getActionResolver();
                    actionResolver.removeActionBean(getClass());
                } catch (Exception e) {
                    logger.warn("Couldn't remove action bean " + this, e);
                }
            }
        } catch (IOException e) {
            logger.error("Error writing script to " + groovyScriptFile, e);
            String msg = getMessage("script.write.failed", groovyScriptFile.getAbsolutePath());
            SessionMessages.addErrorMessage(msg);
        } catch (Exception e) {
            String pageId = pageInstance.getPage().getId();
            logger.warn("Couldn't compile script for page " + pageId, e);
            SessionMessages.addErrorMessage(getMessage("script.compile.failed"));
        } finally {
            IOUtils.closeQuietly(fw);
        }
    }

    protected String getMessage(String key, Object... args) {
        Locale locale = context.getLocale();
        ResourceBundle resourceBundle = application.getBundle(locale);
        String msg = resourceBundle.getString(key);
        return MessageFormat.format(msg, args);
    }

    public Map getOgnlContext() {
        return ElementsThreadLocals.getOgnlContext();
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    //--------------------------------------------------------------------------
    // Utitilities
    //--------------------------------------------------------------------------

    public String getAppJsp(String jsp) {
        return "/apps/" + application.getAppId() + "/web" + jsp;
    }

    public Resolution forwardTo(String page) {
        if(isEmbedded()) {
            return new ForwardResolution(page);
        } else {
            return forwardToPortletPage(page);
        }
    }

    protected Resolution forwardToPortletNotConfigured() {
        if (isEmbedded()) {
            return new ForwardResolution(PAGE_PORTLET_NOT_CONFIGURED);
        } else {
            setupReturnToParentTarget();
            return forwardToPortletPage(PAGE_PORTLET_NOT_CONFIGURED);
        }
    }

    protected Resolution forwardToPortletError(Throwable e) {
        context.getRequest().setAttribute(PORTOFINO_PORTLET_EXCEPTION, e);
        return forwardTo("/layouts/portlet-error.jsp");
    }
}