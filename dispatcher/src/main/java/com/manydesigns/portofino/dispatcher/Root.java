/*
 * Copyright (C) 2016 ManyDesigns srl.  All rights reserved.
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

package com.manydesigns.portofino.dispatcher;

import org.apache.commons.vfs2.FileNotFolderException;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileType;

import javax.ws.rs.container.ResourceContext;
import java.lang.reflect.Constructor;

/**
 * @author Angelo Lupo          - angelo.lupo@manydesigns.com
 * @author Giampiero Granatella - giampiero.granatella@manydesigns.com
 * @author Emanuele Poggi       - emanuele.poggi@manydesigns.com
 * @author Alessio Stalla       - alessio.stalla@manydesigns.com
 */
public class Root extends Node {

    protected ResourceResolver resourceResolver;
    
    public static Root get(FileObject location, ResourceResolver resourceResolver) throws Exception {
        if(!location.exists() || location.getType() != FileType.FOLDER) {
            throw new FileNotFolderException(location);
        }
        Root root;
        Class rootClass = resourceResolver.resolve(location, Class.class);
        if(rootClass != null) {
            if (Root.class.isAssignableFrom(rootClass)) {
                Constructor constructor = rootClass.getConstructor(FileObject.class, ResourceResolver.class);
                root = (Root) constructor.newInstance(location, resourceResolver);
            } else {
                logger.warn(rootClass + " defined in " + location + " does not implement Root, ignoring");
                root = new Root(location, resourceResolver);
            }
        } else {
            root = new Root(location, resourceResolver);
        }
        return root;
    }
    
    public void setResourceContext(ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }
    
    protected Root(FileObject location, ResourceResolver resourceResolver) {
        setLocation(location);
        this.resourceResolver = resourceResolver;
    }

    @Override
    public Resource getParent() {
        return null;
    }

    @Override
    public void setParent(Resource parent) {
        throw new UnsupportedOperationException("Cannot set the parent of the root");
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

}
