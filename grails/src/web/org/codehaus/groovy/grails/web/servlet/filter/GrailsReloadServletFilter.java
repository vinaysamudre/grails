/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.web.servlet.filter;

import groovy.lang.GroovyClassLoader;
import groovy.lang.Writable;
import groovy.text.Template;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.commons.GrailsControllerClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.spring.GrailsRuntimeConfigurator;
import org.codehaus.groovy.grails.commons.spring.GrailsWebApplicationContext;
import org.codehaus.groovy.grails.plugins.GrailsPluginManager;
import org.codehaus.groovy.grails.plugins.PluginManagerHolder;
import org.codehaus.groovy.grails.scaffolding.GrailsTemplateGenerator;
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;
import org.codehaus.groovy.grails.web.servlet.DefaultGrailsApplicationAttributes;
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsUrlHandlerMapping;
import org.codehaus.groovy.grails.web.servlet.mvc.SimpleGrailsController;
import org.codehaus.groovy.grails.web.pages.GroovyPagesTemplateEngine;
import org.codehaus.groovy.grails.web.errors.GrailsWrappedRuntimeException;
import org.springframework.aop.target.HotSwappableTargetSource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

/**
 * A servlet filter that copies resources from the source on content change and manages reloading if necessary
 *
 * @author Graeme Rocher
 * @since Jan 10, 2006
 */
public class GrailsReloadServletFilter extends OncePerRequestFilter {

    public static final Log LOG = LogFactory.getLog(GrailsReloadServletFilter.class);

    ResourceCopier copyScript;
    GrailsWebApplicationContext context;
    GrailsApplication application;

    private GrailsRuntimeConfigurator config;

	private GrailsPluginManager manager;

    public GrailsReloadServletFilter() {
    }


    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
      GrailsApplicationAttributes attrs = new DefaultGrailsApplicationAttributes(getServletContext());
      context = (GrailsWebApplicationContext)attrs.getApplicationContext();

      if(LOG.isDebugEnabled()) {
	      LOG.debug("Executing Grails reload filter...");
      }
      if(context == null) {
          filterChain.doFilter(httpServletRequest,httpServletResponse);
          return;
      }
      application = (GrailsApplication)context.getBean(GrailsApplication.APPLICATION_ID);
      if(application == null) {
          filterChain.doFilter(httpServletRequest,httpServletResponse);
          return;
      }      
      if(config == null) {
    	  WebApplicationContext parent = (WebApplicationContext)getServletContext().getAttribute(GrailsApplicationAttributes.PARENT_APPLICATION_CONTEXT);
    	  config = new GrailsRuntimeConfigurator(application,parent);  
      }
      
      

      if(copyScript == null) {
          GroovyClassLoader gcl = new GroovyClassLoader(getClass().getClassLoader());

          Class groovyClass;
          try {
              groovyClass = gcl.parseClass(gcl.getResource("org/codehaus/groovy/grails/web/servlet/filter/GrailsResourceCopier.groovy").openStream());
              copyScript = (ResourceCopier)groovyClass.newInstance();
          } catch (IllegalAccessException e) {
              LOG.error("Illegal access creating resource copier. Save/reload disabled: " + e.getMessage(), e);
          } catch (InstantiationException e) {
              LOG.error("Error instantiating resource copier. Save/reload disabled: " + e.getMessage(), e);
          } catch (CompilationFailedException e) {
               LOG.error("Error compiling resource copier. Save/reload disabled: " + e.getMessage(), e);
          } catch(Exception e) {
             LOG.error("Error loading resource copier. Save/reload disabled: " + e.getMessage(), e);
          }
        }
	if(LOG.isDebugEnabled()) {
	      LOG.debug("Running copy script...");
	}	
        if(copyScript != null) {
            copyScript.copyResourceBundles();
        } 

        
        if(manager == null) {
        	manager = PluginManagerHolder.getPluginManager();
        }
        try {
            if(manager != null) {
                if(LOG.isDebugEnabled())
                    LOG.debug("Checking Plugin manager for changes..");
                manager.checkForChanges();
            }
            else if(LOG.isDebugEnabled()) {
                LOG.debug("Plugin manager not found, skipping change check");
            }

            filterChain.doFilter(httpServletRequest,httpServletResponse);
        } catch (MultipleCompilationErrorsException mce) {
            if(LOG.isDebugEnabled())
                LOG.debug("Compilation error occured reloading application: " + mce.getMessage(),mce);


            httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            GroovyPagesTemplateEngine engine = attrs.getPagesTemplateEngine();

            Template t = engine.createTemplate(GrailsApplicationAttributes.PATH_TO_VIEWS+"/error.gsp");

            GrailsWrappedRuntimeException wrapped = new GrailsWrappedRuntimeException(getServletContext(), mce);
            Map model = new HashMap();
            model.put("exception", wrapped);

            Writable w = t.make(model);

            w.writeTo(httpServletResponse.getWriter());
            
        }
    }

}
