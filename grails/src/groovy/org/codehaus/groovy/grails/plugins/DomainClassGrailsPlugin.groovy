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
package org.codehaus.groovy.grails.plugins

import org.codehaus.groovy.grails.validation.*
import org.codehaus.groovy.grails.plugins.support.GrailsPluginUtils
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.aop.framework.ProxyFactoryBean
import org.springframework.aop.target.HotSwappableTargetSource
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.springframework.beans.BeanUtils
import org.springframework.validation.Errors
import org.springframework.validation.BindException
import org.codehaus.groovy.runtime.metaclass.ThreadManagedMetaBeanProperty    
import org.springframework.web.context.request.RequestContextHolder as RCH       

/**
* A plug-in that configures the domain classes in the spring context
*
* @author Graeme Rocher
* @since 0.4
*/
class DomainClassGrailsPlugin {
	
	def version = grails.util.GrailsUtil.getGrailsVersion()
	def dependsOn = [i18n:version]
	def loadAfter = ['hibernate', 'controllers']
	
	def doWithSpring = {
		for(dc in application.domainClasses) {
		    // Note the use of Groovy's ability to use dynamic strings in method names!
		    "${dc.fullName}"(dc.getClazz()) { bean ->
				bean.singleton = false
				bean.autowire = "byName"						
			}
			"${dc.fullName}DomainClass"(MethodInvokingFactoryBean) {
				targetObject = ref("grailsApplication", true)
				targetMethod = "getArtefact"
				arguments = [DomainClassArtefactHandler.TYPE, dc.fullName]
			}
			"${dc.fullName}PersistentClass"(MethodInvokingFactoryBean) {
				targetObject = ref("${dc.fullName}DomainClass")
				targetMethod = "getClazz"        						
            }
            "${dc.fullName}Validator"(GrailsDomainClassValidator) {
                messageSource = ref("messageSource")
                domainClass = ref("${dc.fullName}DomainClass")                
            }

		}
	}

	def doWithDynamicMethods = { ctx->
        for(GrailsDomainClass dc in application.domainClasses) {
			def domainClass = dc
            MetaClass metaClass = domainClass.metaClass

            metaClass.ident = {-> delegate[domainClass.identifier.name] }
            metaClass.'static'.getConstraints = {->    
                domainClass.constrainedProperties
            }
            metaClass.getConstraints = {->
                domainClass.constrainedProperties
            }
            
            metaClass.'static'.create = {-> BeanUtils.instantiateClass(domainClass.getClazz()) }
            metaClass.hasErrors = {-> delegate.errors?.hasErrors() }
			if(manager.hasGrailsPlugin("controllers")) {
				metaClass.getErrors = {->
				     RCH.currentRequestAttributes().currentRequest["org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"]
			   	}                                                                                                          
				metaClass.setErrors = { Errors errors ->
					RCH.currentRequestAttributes().currentRequest["org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"] = errors
			    }
			}   
			else {
				metaClass.errors = new ThreadManagedMetaBeanProperty(domainClass.clazz, "errors", Errors, { 
	                object -> object ? new BindException( object, object.getClass().getName()) : null
	            })
			}
        }
	}
}