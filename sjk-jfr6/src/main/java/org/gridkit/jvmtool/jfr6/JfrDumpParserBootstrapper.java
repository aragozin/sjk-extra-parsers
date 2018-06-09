/**
 * Copyright 2017 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.jvmtool.jfr6;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.gridkit.jvmtool.event.EventDumpParser;

public class JfrDumpParserBootstrapper {

	private static final String PARSER_PACKAGE = "org.gridkit.jvmtool.jfr6.parser";
	private static final String PARSER_CLASS = PARSER_PACKAGE + ".JfrDumpParser";
	
	List<URL> jmcPath = new ArrayList<URL>();
	
	public EventDumpParser load() {

		String javaHome = System.getProperty("java.home");
		File f = new File(javaHome);		
		File jmcp = mcpath(f);
		if (!mcpath(f).isDirectory()) {
			if (javaHome.endsWith("jre")) {
				f = new File(javaHome.substring(0, javaHome.length() - 4));
				if (mcpath(f).isDirectory()) {
					jmcp = mcpath(f);
				}
			}
		}
		if (jmcp.isDirectory() && jmcp.listFiles() != null) {
			for(File j: jmcp.listFiles()) {
				try {
					if (j.isFile() && j.getName().startsWith("com.oracle.jmc.common") && j.getName().endsWith(".jar")) {
						jmcPath.add(j.toURI().toURL());
					}
				} catch (MalformedURLException e) {
					// ignore
				}
				try {
					if (j.isFile() && j.getName().startsWith("com.oracle.jmc.flightrecorder") && j.getName().endsWith(".jar")) {
						jmcPath.add(j.toURI().toURL());
					}
				} catch (MalformedURLException e) {
					// ignore
				}
			}
		}
		
		if (jmcPath.isEmpty()) {
			return null;
		}
		
		jmcPath.add(selfJar());
		
		JmcClassLoader cl = new JmcClassLoader(jmcPath.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
		
		try {
			return new Instantiator(cl).call();
		}
		catch(Throwable e) {
			// ignore
		}
		
		try {
			return new Instantiator(Thread.currentThread().getContextClassLoader()).call();
		}
		catch(Throwable e) {
			// ignore
		}
		
		return null;
	}

	private URL selfJar() {
		ClassLoader cl = this.getClass().getClassLoader();
		String res = this.getClass().getName().replace('.', '/') + ".class";
		URL url = cl.getResource(res);
		if (url != null) {
			String surl = url.toString();
			if (surl.endsWith(res)) {
				String turl = surl.substring(0, surl.length() - res.length());
				try {
					return new URL(turl);
				} catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return url;
	}

	private File mcpath(File f) {
		return new File(new File(new File(f, "lib"), "missioncontrol"), "plugins");
	}

	static class JmcClassLoader extends URLClassLoader {

		private final ClassLoader baseClassloader;
		
		public JmcClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, null);
			baseClassloader = parent;
		}

	    public URL getResource(String name) {
	    	URL url = null;
	    	if (baseClassloader != null) {
	    		url = baseClassloader.getResource(name);
	    	}
	    	if (url == null) {
	    	    url = findResource(name);
	    	}
	    	return url;
        }

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			if (shouldLoad(name)) {
				String bytepath = name.replace('.', '/') + ".class";
				URL url = getResource(bytepath);
				if (url == null) {
					throw new ClassNotFoundException(name);
				}
				Class<?> cl;
				try {
					cl = findLoadedClass(name);
					if (cl == null) {
						cl = findClass(name);
					}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					throw e;
				}
				if (cl == null) {
					throw new ClassNotFoundException(name);
				}					
				return cl;				
			}
			Class<?> cc = baseClassloader.loadClass(name);
			return cc;
		}

		private boolean shouldLoad(String name) {
			return name.startsWith(PARSER_PACKAGE) || name.startsWith("com.oracle.jmc");
		}		
	}
	
	static class Instantiator implements Callable<EventDumpParser> {

		final ClassLoader cl;
		
		public Instantiator(ClassLoader cl) {
			this.cl = cl;
		}

		@Override
		public EventDumpParser call() throws Exception {
			return (EventDumpParser) cl.loadClass(PARSER_CLASS).newInstance();
		}
	}
}
