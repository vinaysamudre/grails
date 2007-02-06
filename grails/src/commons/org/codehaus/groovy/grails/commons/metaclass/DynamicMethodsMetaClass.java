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
package org.codehaus.groovy.grails.commons.metaclass;

import groovy.lang.MetaClass;
import groovy.lang.MetaClassImpl;
import groovy.lang.GroovyObject;

import java.beans.IntrospectionException;

import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * This MetaClass uses the DynamicMethods interface to allow for more powerful ways of adding new constructors,
 * properties, static and instance methods.
 * 
 * A DynamicMethods instance contains, for example, a implementation of DynamicMethodInvocation that can use regex
 * to match any number of method patterns. This mechanism is used to implement the dynamic finder methods found
 * in GORM (see http://grails.org/GORM)
 * 
 * @author Steven Devijver
 * @author Graeme Rocher
 * 
 * @since Aug 8, 2005
 */
public class DynamicMethodsMetaClass extends MetaClassImpl implements AdapterMetaClass {

	DynamicMethods dynamicMethods;
	MetaClass adaptee = null;


    /**
	 * @return the adaptee
	 */
	public MetaClass getAdaptee() {
		return adaptee;
	}

	public DynamicMethodsMetaClass(Class clazz, DynamicMethods dynamicMethods)
			throws IntrospectionException {
		this(clazz,dynamicMethods,true);
	}

	public DynamicMethodsMetaClass(Class clazz)
			throws IntrospectionException {
		this(clazz,null,false);
	}

    public DynamicMethodsMetaClass(Class clazz, DynamicMethods dynamicMethods, boolean inRegistry)
			throws IntrospectionException {
		super(InvokerHelper.getInstance().getMetaRegistry(), clazz);
        if(dynamicMethods != null)
            this.dynamicMethods = dynamicMethods;
        else
            this.dynamicMethods = new DefaultDynamicMethods(clazz);
        
        adaptee = registry.getMetaClass(clazz);
		if(inRegistry) {
			registry.setMetaClass(clazz, this);
		}
	}

    public DynamicMethods getDynamicMethods() {
        return dynamicMethods;
    }

    public Object invokeStaticMethod(Object target, String methodName, Object[] arguments) {
		InvocationCallback callback = new InvocationCallback();
		Object returnValue = this.dynamicMethods.invokeStaticMethod(target, methodName, arguments, callback);
		if (callback.isInvoked()) {
			return returnValue;
		} else {
			return adaptee.invokeStaticMethod(target, methodName, arguments);
		}
	}

    public void setProperty(Class aClass, Object object, String property, Object newValue, boolean b, boolean b1) {
        InvocationCallback callback = new InvocationCallback();
        this.dynamicMethods.setProperty(object,property,newValue,callback);
        if (!callback.isInvoked()) {
            adaptee.setProperty(object, property, newValue);
        }
    }

    public Object getProperty(Class aClass, Object object, String property, boolean b, boolean b1) {
        InvocationCallback callback = new InvocationCallback();
        Object returnValue = this.dynamicMethods.getProperty(object,property,callback);
        if (callback.isInvoked()) {
            return returnValue;
        } else {
            return adaptee.getProperty(object,property);
        }

    }/* (non-Javadoc)
	 * @see groovy.lang.MetaClassImpl#invokeConstructor(java.lang.Object[])
	 */
	public Object invokeConstructor(Object[] arg0) {
		InvocationCallback callback = new InvocationCallback();
		Object instance = this.dynamicMethods.invokeConstructor(arg0,callback);
		if(callback.isInvoked()) {
			return instance;
		}
		else {
			return adaptee.invokeConstructor(arg0);
		}
	}

    public Object invokeMethod(Class aClass, Object target, String methodName, Object[] arguments, boolean b, boolean b1) {
        InvocationCallback callback = new InvocationCallback();
        Object returnValue = this.dynamicMethods.invokeMethod(target, methodName, arguments, callback);
        if (callback.isInvoked()) {
            return returnValue;
        } else {
            return adaptee.invokeMethod(target, methodName, arguments);
        }

    }


    public void setAdaptee(MetaClass a) {
		this.adaptee = a;
	}
}
