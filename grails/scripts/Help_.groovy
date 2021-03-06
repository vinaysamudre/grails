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

/**
 * Gant script that evaluates all installed scripts to create help output
 * 
 * @author Graeme Rocher
 *
 * @since 0.4
 */

import grails.util.GrailsNameUtils

includeTargets << grailsScript("Init")
    
class HelpEvaluatingCategory {

    static defaultTask = ""
    static helpText = [:]
	static target(Object obj, Map args, Closure callable) {
        def entry = args.entrySet().iterator().next()
        helpText[(entry.key)] = entry.value

        if (entry.key == "default") {
            defaultTask = "default"
        }
	}
	static getDefaultDescription(Object obj) {
		return helpText[defaultTask]
	}

    static setDefaultTarget(Object obj, val) {
        defaultTask = val
    }
	
}

File getHelpFile(File script) {
    File helpDir = new File(grailsTmp, "help")
    if (!helpDir.exists()) helpDir.mkdir()
    String scriptname = script.getName()
	return new File(helpDir, scriptname.substring(0, scriptname.lastIndexOf('.')) + ".txt")
}

boolean shouldGenerateHelp(File script) {
	File file = getHelpFile(script)
    return (!file.exists() || file.lastModified() < script.lastModified() )
}
                                

target ( 'default' : "Prints out the help for each script") {
	ant.mkdir(dir:grailsTmp)    	
	def scripts = getAllScripts().collect { it.file }
        
	def helpText = ""
      
	
	if(args) {  
		def fileName = GrailsNameUtils.getNameFromScript(args)
		def file = scripts.find {
            def scriptFileName = it.name[0..-8]
            if(scriptFileName.endsWith("_")) scriptFileName = scriptFileName[0..-2]
            scriptFileName == fileName
        }

        if(file) {            
            println """
    Usage (optionals marked with *):
    grails [environment]*
            """
            def gcl = new GroovyClassLoader()
            use(HelpEvaluatingCategory.class) {
                if (shouldGenerateHelp(file)) {
                    try {
                        def script = gcl.parseClass(file).newInstance()
                        script.binding = binding
                        script.run()

                        def scriptName = GrailsNameUtils.getScriptName(file.name)

                        helpText = "grails ${scriptName} -- ${getDefaultDescription()}"
                        File helpFile = getHelpFile(file)
                        if(!helpFile.exists())
                            helpFile.createNewFile()
                        helpFile.write(helpText)
                    }
                    catch(Throwable t) {
                        println "Warning: Error caching created help for ${file}: ${t.message}"
                        println helpText
                    }
                } else {
                    helpText = getHelpFile(file).text
                }
                println helpText
            }
        }
        else {
            println "No script found for name: $args"
        }

	}
	else {
			println """
Usage (optionals marked with *):
grails [environment]* [target] [arguments]*

Examples:
grails dev run-app
grails create-app books

Available Targets (type grails help 'target-name' for more info):"""
		
	    scripts.unique { it.name }. sort{ it.name }.each { file ->
			def scriptName = GrailsNameUtils.getScriptName(file.name)
			println "grails ${scriptName}"   
		}		
	}  
}                                               

target( showHelp: "Show help for a particular command") {
	def gcl = new GroovyClassLoader()    				
	use(HelpEvaluatingCategory.class) {    
		if (shouldGenerateHelp(file)) {
			try {
				def script = gcl.parseClass(file).newInstance()			
				script.binding = binding
				script.run()

				def scriptName = GrailsNameUtils.getScriptName(file.name)

				helpText = "grails ${scriptName} -- ${getDefaultDescription()}"
				getHelpFile(file).write(helpText) 		  		
			}                                                      
			catch(Throwable t) {
				println "Error creating help for ${file}: ${t.message}"
				t.printStackTrace(System.out)
			}
		} else {
			helpText = getHelpFile(file).text
		}
		println helpText  
	}	   		
	
}
    