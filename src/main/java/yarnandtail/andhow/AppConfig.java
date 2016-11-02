package yarnandtail.andhow;

import yarnandtail.andhow.ConfigParamUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 *
 * @author eeverman
 */
public class AppConfig {
	
	public static final String DEFAULT_CMD_NAME_VALUE_SEPARATOR = "=";
	private static final String PROP_FILE_NAME = "config.properties";
	private static final String DEFAULT_PROP_FILE_NAME = "default_config.properties";
	private static final String USER_CONF_DIR = "~/";
	private static final String EXECUTABLE_CONF_DIR = "";
	private static final String[] DIRECTORY_SEARCH_PATHS = new String[] {EXECUTABLE_CONF_DIR, USER_CONF_DIR};
	
	
	
	/** A List of Lists of ConfigParamEnums */
	List<List<ConfigPoint>> configParamEnumLists;
	
	List<String> configParamListNames;
	
	List<ConfigParamValue> configParams;
	
	boolean copyToSysProps;
	boolean verboseConfig;
	
	public AppConfig(List<List<ConfigPoint>> configParamEnumLists, String[] configParamSetNames, 
			String[] args, boolean copyToSysProps) {
		
		PrintStream ps = System.out;
		
		List<ConfigParamValue> configParams = ConfigParamUtil.parseCommandArgs(configParamEnumLists, args, DEFAULT_CMD_NAME_VALUE_SEPARATOR);
		verboseConfig = containsVerboseConfigRequest(configParams);
		
		
		if (! isValid(configParams)) {
			ConfigParamUtil.printParams(configParams, ps, "=== INVALID PARAMS FOUND ON THE COMMAND LINE ===");
			printAppSpecificCommandHelp(ps);
			ConfigParamUtil.printHelpForParams(configParamEnumLists, configParamSetNames, ps, null);
			return;
		} else if (verboseConfig) {
			ConfigParamUtil.printParams(configParams, ps, "=== Verbose Configuration: Parameters found on the command line ===");
		}
		
		if (containsHelpRequest(configParams)) {
			printAppSpecificCommandHelp(ps);
			ConfigParamUtil.printHelpForParams(configParamEnumLists, configParamSetNames, ps, "HELP request found in command line");
			return;
		}
		
		//
		//Edge cases handled from cmd line - continue to props file
		boolean skipDirectory = findOneParamValueBooleanByType(configParams, ParamType.SKIP_PROPERTIES_FROM_FILE_SYSTEM, false);
		if (skipDirectory) {
			PropertyFileWrap propWrap = findPropertiesFile(configParams);
		} else {
			if (verboseConfig) {
				ps.println("Skipping property file loading from directory structure due to  flag");
			}
		}
		
		
	}
	
	protected PropertyFileWrap findPropertiesFile(List<ConfigParamValue> cmdLineConfigParams) {
		String fileName = findOneParamValueStringByType(cmdLineConfigParams, ParamType.PROPERTIES_FILE_NAME, PROP_FILE_NAME);
		List<String> dirPaths = findParamValueStringsByType(cmdLineConfigParams, ParamType.PROPERTIES_FILE_SYSTEM_PATH, Arrays.asList(DIRECTORY_SEARCH_PATHS));
		String defaultFileName = findOneParamValueStringByType(cmdLineConfigParams, ParamType.PROPERTIES_DEFAULT_FILE_NAME, DEFAULT_PROP_FILE_NAME);

		
		PropertyFileWrap wrap = null;
		
		wrap = findPropertiesFile(dirPaths, fileName);
		
		if (wrap.properties == null) {
			wrap = findPropertiesFile(dirPaths, defaultFileName);
		}
		
		return wrap;
	}
	
	protected PropertyFileWrap findPropertiesFile(List<String> dirPaths, String fileName) {

		PropertyFileWrap wrap = null;
		
		for (String path : dirPaths) {
			
			File propFile = ConfigParamUtil.getFile(path, fileName);
			
			if (propFile != null && propFile.exists()) {
				Properties props = new Properties();

				try (FileInputStream in = new FileInputStream(propFile)) {
					props.load(in);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

				wrap = new PropertyFileWrap();
				wrap.originalDir = path;
				wrap.originalName = fileName;
				wrap.expandedPath = propFile.getAbsolutePath();
				wrap.properties = props;
			}
			
		}
		
		return wrap;
	}
	
	/**
	 * Search all supplied params, in order, and return a list of ones of the
 specified ConfigPoint.
	 * 
	 * @param params
	 * @return 
	 */
	private List<ConfigParamValue> findParamsByType(List<ConfigParamValue> params, ParamType type) {
		List<ConfigParamValue> found = new ArrayList();
		params.stream().filter(p -> type.equals(p.getParamType())).forEachOrdered(p -> found.add(p));
		return found;
	}
	
	/**
	 * Search all supplied params, in order, and return a list of the values of
 ones of the specified ConfigPoint.
	 * Only non-null values or flags are returned.  Name/value types w/ no value
	 * are skipped.
	 * This uses the effectiveValue, so Flag values will return a Boolean true if present.
	 * 
	 * @param params
	 * @return 
	 */
	private List<Object> findParamValuesByType(List<ConfigParamValue> params, ParamType type) {
		List<Object> found = new ArrayList();
		params.stream().filter(p -> type.equals(p.getParamType()) && p.getEffectiveValue() != null).forEachOrdered(p -> found.add(p.getEffectiveValue()));
		return found;
	}
	
	/**
	 * Search all supplied params, in order, and return a list of the stringified
	 * values of ones of the specified type.
	 * Only non-null values or flags are returned.  Name/value types w/ no value
	 * are skipped.
	 * Flags return their true/false value as a string.
	 * 
	 * @param params
	 * @return 
	 */
	private List<String> findParamValueStringsByType(List<ConfigParamValue> params, ParamType type, List<String> defaultValues) {
		List<String> found = new ArrayList();
		params.stream().filter(p -> type.equals(p.getParamType()) && p.getEffectiveValue() != null).forEachOrdered(p -> found.add(p.getEffectiveValueString()));
		return (found.size() > 0)?found:defaultValues;
	}
	
	/**
	 * Search all supplied params, and return the first one that matches the
	 * specified type.
	 * 
	 * @param params
	 * @return 
	 */
	private ConfigParamValue findOneParamByType(List<ConfigParamValue> params, ParamType type) {
		return params.stream().filter(p -> type.equals(p.getParamType())).findFirst().orElse(null);
	}
	
	/**
	 * Search all supplied params for the requested type and return the String value. 
	 * Only non-null values or flags are considered.
	 * Flags return their true/false value as a string.
	 * @param params
	 * @return A Single String or null.
	 */
	private String findOneParamValueStringByType(List<ConfigParamValue> params, ParamType type, String defValue) {
		String found = params.stream().filter(p -> type.equals(p.getParamType())).findFirst().map(p -> p.getEffectiveValueString()).orElse(null);
		return (found != null)?found:defValue;
	}
	
	/**
	 * Search all supplied params for the requested type and return the Boolean value. 
	 * Only non-null values or flags are considered.
	 * Name/value pairs are attempted to be read as boolean.
	 * @param params
	 * @return A Single String or null.
	 */
	private boolean findOneParamValueBooleanByType(List<ConfigParamValue> params, ParamType type, Boolean defValue) {
		Boolean found = params.stream().filter(p -> type.equals(p.getParamType())).findFirst().map(p -> p.isTrue()).orElse(null);
		return (found != null)?found:defValue;
	}
	
	private boolean isValid(List<ConfigParamValue> params) {
		return params.stream().anyMatch(p -> ! p.isValid());
	}
	
	private boolean containsHelpRequest(List<ConfigParamValue> params) {
		return params.stream().anyMatch(p -> 
				ParamType.HELP_FLAG.equals(p.getParamType()) && p.isTrue());
	}
	
	private boolean containsVerboseConfigRequest(List<ConfigParamValue> params) {
		return params.stream().anyMatch(p -> 
				ParamType.VERBOSE_CONFIG_FLAG.equals(p.getParamType()) && p.isTrue());
	}
	
	public void printAppSpecificCommandHelp(PrintStream ps) {
		//Override to provide command line help.  Configuration params are already handled.
	}
	
	public static class PropertyFileWrap {
		public String originalDir;
		public String originalName;
		public String expandedPath;
		public Properties properties;
	}
	
	
}
