package org.reso.service.servlet;

import org.apache.olingo.server.api.*;
import org.reso.service.tenant.ODataHandlerCache;
import org.reso.service.tenant.ClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import javax.servlet.ServletException;
import javax.servlet.http.*;

public class RESOservlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(RESOservlet.class);

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        
        String clientId = (String) req.getAttribute("client_id");
        ClientContext.setCurrentClient(clientId);
        
        try {
            // Get or create handler for thet certification report of this client
            ODataHttpHandler handler = ODataHandlerCache.getHandler(clientId);
            
            handler.process(req, resp);
            
        } catch (RuntimeException e) {
            LOG.error("Server Error occurred in RESOservlet for client: " + clientId, e);
            throw new ServletException(e);
        } finally {
            ClientContext.clear();
        }
    }

}