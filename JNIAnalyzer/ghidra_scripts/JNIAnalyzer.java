//Applies the standard JNI parameter types to the `JNI_OnLoad` function
//as well as all functions starting with `Java_`.
//
//`Java_` functions with less than two parameters are left untouched.
//The appropriate function parameters should be applied manually to them.
//@author Ayrx
//@category JNI
//@keybinding 
//@menupath 
//@toolbar 

import generic.jar.ResourceFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import docking.widgets.filechooser.GhidraFileChooser;
import ghidra.app.plugin.core.datamgr.archive.Archive;
import ghidra.app.script.GhidraScript;
import ghidra.app.services.DataTypeManagerService;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.util.*;
import ghidra.program.model.reloc.*;
import ghidra.program.model.data.*;
import ghidra.program.model.block.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.scalar.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.lang.*;
import ghidra.program.model.pcode.*;
import ghidra.program.model.address.*;
import ghidra.util.exception.*;
import ghidra.app.plugin.core.datamgr.archive.DuplicateIdException;

public class JNIAnalyzer extends GhidraScript {

	DataTypeManager manager;
	
	private class MethodInformation {
		private String methodName;
		private ArrayList<String> argumentTypes;
	}
	
	private class NativeMethodsList {
		ArrayList<MethodInformation> methods = new ArrayList<>();
	}
	
    public void run() throws Exception {
    	println("[+] Import jni_all.h...");
    	this.manager = this.getDataTypeManageFromArchiveFile();
    	
    	File infoFile = this.askFile("Select method argument file", "Open");
    	Gson gson = new Gson();
    	JsonReader reader = new JsonReader(new FileReader(infoFile));

    	NativeMethodsList methodsList = gson.fromJson(reader, NativeMethodsList.class);
    	for (MethodInformation method : methodsList.methods) {
    		println(method.methodName);
    		println(method.argumentTypes.get(0));
    		break;
    	}
    		
    	println("[+] Enumerating JNI functions...");
    	HashMap<String, Function> functions = new HashMap<String, Function>();
    	Function function = this.getFirstFunction();
    	while (function != null) {
    		if (function.getName().startsWith("Java_")) {
    			functions.put(function.getName(), function);
    			println(function.getName());
    		} 
    		
    		if (function.getName().equals("JNI_OnLoad")) {
    			this.applyJNIOnLoadSignature(function);
    		}
    		
    		function = this.getFunctionAfter(function);
		}
    	println("Total JNI functions found: " + functions.size());
    	
    	println("[+] Applying function signatures...");
    	for (MethodInformation method : methodsList.methods) {
    		String methodName = method.methodName;
    		String[] methodNameSplit = methodName.split("\\.");
    		methodName = "Java_" + String.join("_", methodNameSplit);
    		
    		if (functions.containsKey(methodName)) {
    			Function f = functions.get(methodName);
    			
    			Parameter[] params = new Parameter[method.argumentTypes.size() + 2]; // + 2 to accomodate env and thiz
    			
    			params[0] = new ParameterImpl(
					"env", 
			    	this.manager.getDataType("/jni_all.h/JNIEnv *"),
			    	this.currentProgram,
			    	SourceType.USER_DEFINED
			    );
    			
    			params[1] = new ParameterImpl(
					"thiz", 
			    	this.manager.getDataType("/jni_all.h/jobject"),
			    	this.currentProgram,
			    	SourceType.USER_DEFINED
			    );
    			
    			for (int i = 0; i < method.argumentTypes.size(); i++) {
    				String argType = method.argumentTypes.get(i);
    				
    				params[i + 2] = new ParameterImpl(
						"a" + String.valueOf(i), 
				    	this.manager.getDataType("/jni_all.h/" + argType),
				    	this.currentProgram,
				    	SourceType.USER_DEFINED
				    );
    			}

				f.updateFunction(null, null, 
    					Function.FunctionUpdateType.DYNAMIC_STORAGE_FORMAL_PARAMS, true, SourceType.USER_DEFINED, params);    			
    		}
    	}  	
    }
    
    private DataTypeManager getDataTypeManageFromArchiveFile() throws IOException, DuplicateIdException {
    	PluginTool tool = this.state.getTool();
    	DataTypeManagerService service = tool.getService(DataTypeManagerService.class);
    	
    	// Look for an already open "jni_all" archive.
		DataTypeManager[] managers = service.getDataTypeManagers();
		for (DataTypeManager m : managers) {
			if (m.getName().equals("jni_all")) {
				return m;
			}
		}
		
		// If an existing archive isn't found, open it from the file.	
		URL jniArchiveURL = ClassLoader.getSystemClassLoader().getResource("jni_all.gdt");		
		File jniArchiveFile = new File(jniArchiveURL.getFile());
		
    	Archive jniArchive = service.openArchive(jniArchiveFile, false);
    	return jniArchive.getDataTypeManager();
    }
    
    private void applyJNIOnLoadSignature(Function function) throws DuplicateNameException, InvalidInputException {
    	println("Modified " + function.getName());
    	
    	Parameter arg0 = function.getParameter(0);
    	arg0.setName("vm", SourceType.USER_DEFINED);
    	arg0.setDataType(
	    	this.manager.getDataType("/jni_all.h/JavaVM *"),
			SourceType.USER_DEFINED
		);
    }	   
}