/*
 * Copyright 2007 - 2021 Ralf Wisser.
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
package net.sf.jailer.ui;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import javax.swing.JOptionPane;

import net.sf.jailer.ExecutionContext;
import net.sf.jailer.configuration.Configuration;
import net.sf.jailer.render.HtmlDataModelRenderer;
import net.sf.jailer.ui.util.AWTWatchdog;
import net.sf.jailer.util.LogUtil;

/**
 * Sets up environment.
 *
 * @author Ralf Wisser
 */
public class Environment {

	private static File home = null;

	public static String[] init(String[] args) {
		// see:
		// https://github.com/AdoptOpenJDK/openjdk-jdk11/issues/10
		// https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8215200
		// https://bugs.openjdk.java.net/browse/JDK-8215200
		System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");

		try {
			java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
			rootLogger.setLevel(Level.SEVERE);
			for (Handler h : rootLogger.getHandlers()) {
			    h.setLevel(Level.SEVERE);
			}
			System.setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");
		} catch (Exception e) {
		}

		boolean jpack = false;
		File jPackApp = null;
		if (args != null) {
			List<String> aList = new ArrayList<String>(Arrays.asList(args));
			if (aList.remove("-jpack")) {
				args = aList.toArray(new String[0]);
				jpack = true;
				URL url = Environment.class.getProtectionDomain().getCodeSource().getLocation();
				try {
					jPackApp = new File(url.toURI()).getParentFile();
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
				File configFile = new File(jPackApp, "jailer.xml");
				if (!configFile.exists()) {
					throw new IllegalStateException("missing file \"" + configFile + "\". Base URL: " + url);
				}
			}
		}

		initUI();
		try {
			File app;
			// app = new File("lib", "app"); // Linux
			if (jpack) {
				applicationBase = jPackApp;
			} else {
				app = new File("app"); // Windows
				if (new File(app, "jailer.xml").exists()) {
					applicationBase = app;
				} else {
					// not yet supported
//					app = new File("Contents", "app"); // macOS
//					if (new File(app, "jailer.xml").exists()) {
//						applicationBase = app;
//					}
				}
			}
			Configuration.applicationBase = applicationBase;
		} catch (Throwable t) {
			// ignore
		}

		if (new File(".singleuser").exists() // legacy
				|| new File(".multiuser").exists()
				|| isJPacked()) {
			home = new File(System.getProperty("user.home"), ".jailer");
			home.mkdirs();
			LogUtil.reloadLog4jConfig(home);
			Configuration configuration = Configuration.getInstance();
			try {
				copyIfNotExists("datamodel");
				copyIfNotExists("bookmark");
				copyIfNotExists("extractionmodel");
				copyIfNotExists("layout");
				copyIfNotExists("demo-scott-1.4.mv.db");
				copyIfNotExists("demo-sakila-1.4.mv.db");
				copyIfNotExists("demo-scott-subset-1.4.mv.db");
				copyIfNotExists("example");
				copyIfNotExists("render");

				if (isJPacked()) {
					File lib = newFile("lib");
					lib.mkdirs();
					File jdbcJar = newWorkingFolderFile("jdbc_lib");
					if (jdbcJar.exists() && jdbcJar.isDirectory()) {
						String[] fl = jdbcJar.list();
						if (fl != null) {
							for (String f: fl) {
								String nf = f.replaceAll("\\.x$", "");
								if (!nf.equals(f)) {
									File tf = new File(lib, nf);
									if (!tf.exists()) {
										try {
											Files.copy(new File(jdbcJar, f).toPath(), tf.toPath());
										} catch (Throwable t) {
											// ignore
										}
									}
								}
							}
						}
					}
				}

				configuration.setTempFileFolder(newFile("tmp").getPath());
				HtmlDataModelRenderer renderer = configuration.getRenderer();
				if (renderer != null) {
					renderer.setOutputFolder(newFile(renderer.getOutputFolder()).getAbsolutePath());
				}
				ExecutionContext.defaultDatamodelFolder = newFile(ExecutionContext.defaultDatamodelFolder).getAbsolutePath();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			if (!testCreateTempFile()) {
				UIUtil.showException(null, "Error", new IllegalStateException("No write permission on "
						+ new File(".").getAbsolutePath() + " \n"
						+ "To setup multi-user mode, create a (empty) file named \".multiuser\" in this folder. "
						+ "All model and settings files are then stored in a folder named \".jailer\" in the user's home directory."));
				System.exit(-1);
			}
		}
		int stateOffset = 100;
		state = (new File(".singleuser").exists() ? 1 : 0) // legacy
				+ (new File(".multiuser").exists() ? 2 : 0)
				// + 4 no longer used
				+ (!testCreateTempFile() ? 8 : 0)
				+ (isJPacked() ? 1000 : 0)
				+ stateOffset;
		AWTWatchdog.start();
		LogUtil.setWarn(new LogUtil.Warn() {
			@Override
			public void warn(Throwable t) {
				StringWriter sw = new StringWriter();
		        PrintWriter pw = new PrintWriter(sw);
		        t.printStackTrace(pw);
		        UIUtil.sendIssue("warn", sw.toString().replaceAll("at (.*)?\\.((\\w|\\$)+\\.(\\w|\\$)+\\()", "$2"));
		        LogUtil.setWarn(null);
			}
		});

		return args;
	}

	private static File applicationBase = null;

	public static File newWorkingFolderFile(String name) {
		if (applicationBase == null || new File(name).isAbsolute()) {
			return new File(name);
		}
		return new File(applicationBase, name);
	}

	public static boolean isJPacked() {
		return applicationBase != null;
	}

	private static void initUI() {
		try {
			EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
			queue.push(new JEventQueue());
		} catch (Throwable t) {
			// ignore
		}
	}

	public static class JEventQueue extends EventQueue {
		int activeCD = 5;

		@Override
		protected void dispatchEvent(AWTEvent newEvent) {
		    try {
		        super.dispatchEvent(newEvent);
		    } catch (Throwable t) {
		    	if (t instanceof OutOfMemoryError) {
	        		JOptionPane.showMessageDialog(null, "Out of memory!" + (t.getMessage() != null? " (" + t.getMessage() + ")" : ""), "Error", JOptionPane.ERROR_MESSAGE);
	        	}
	        	if (activeCD > 0) {
		        	--activeCD;
		        	try {
		        		UIUtil.showException(null, "Error", t, "AWT");
				    } catch (Throwable t2) {
				    	UIUtil.showException(null, "Error", t2, "AWT2");
				    }
		        } else {
		        	throw t;
		        }
		    }
		}
	}

	private static boolean copyIfNotExists(String f) throws IOException {
		File sFile = newWorkingFolderFile(f);
		File dFile = newFile(f);

		if (!sFile.exists()) {
			return false;
		}

		Path sourcePath = sFile.toPath();
		Path targetPath = dFile.toPath();
		Files.walkFileTree(sourcePath, new CopyFileVisitor(targetPath));
		return true;
	}

	static class CopyFileVisitor extends SimpleFileVisitor<Path> {
		private final Path targetPath;
		private Path sourcePath = null;

		public CopyFileVisitor(Path targetPath) {
			this.targetPath = targetPath;
		}

		@Override
		public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
			if (sourcePath == null) {
				sourcePath = dir;
			}
			Files.createDirectories(targetPath.resolve(sourcePath.relativize(dir)));
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
			try {
				Path target = sourcePath == null ? targetPath : targetPath.resolve(sourcePath.relativize(file));
				if (!target.toFile().exists()) {
					Files.copy(file, target);
				}
			} catch (Exception e) {
				// ignore
			}
			return FileVisitResult.CONTINUE;
		}
	}

	public static File newFile(String name) {
		if (home == null || new File(name).isAbsolute()) {
			return new File(name);
		}
		return new File(home, name);
	}

	public static boolean testCreateTempFile() {
		try {
			File tempFile = new File("tp" + new Random().nextInt(100000));
			FileOutputStream out = new FileOutputStream(tempFile);
			out.write(0);
			out.close();
			tempFile.delete();
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	public static int state;

	public static boolean nimbus = false;

}
