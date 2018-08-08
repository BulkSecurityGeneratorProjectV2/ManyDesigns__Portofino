package com.manydesigns.portofino.dispatcher.web;

import com.manydesigns.portofino.dispatcher.Root;
import com.manydesigns.portofino.dispatcher.swagger.DocumentedApiRoot;
import org.apache.commons.configuration2.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Created by alessio on 4/28/16.
 */
@Path("/")
public class ApplicationRoot extends DocumentedApiRoot {

    protected static final Logger logger = LoggerFactory.getLogger(ApplicationRoot.class);
    
    @Context
    protected ServletContext servletContext;
    
    @Context
    protected ResourceContext resourceContext;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response homeHTML() throws URISyntaxException {
        return redirectToHome("home.html");
    }
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response homeJSON() throws URISyntaxException {
        return redirectToHome("home.json");
    }
    
    @GET
    @Produces("application/yaml")
    public Response homeYAML() throws URISyntaxException {
        return redirectToHome("home.yaml");
    }

    protected Response redirectToHome(String home) throws URISyntaxException {
        Configuration configuration = getConfiguration();
        String homeLocation = configuration.getString(home);
        if(homeLocation != null) {
            return Response.temporaryRedirect(new URI(homeLocation)).build();
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    protected Configuration getConfiguration() {
        return (Configuration) servletContext.getAttribute("portofino.configuration");
    }

    @Path("")
    public Root start() throws Exception {
        Root root = rootFactory.createRoot();
        resourceContext.initResource(root);
        return root;
    }

}
