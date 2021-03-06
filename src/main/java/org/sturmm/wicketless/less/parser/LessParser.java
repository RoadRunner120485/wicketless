/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sturmm.wicketless.less.parser;

import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.wicket.util.string.AppendingStringBuffer;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.tools.shell.Global;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sturmm.wicketless.less.source.ClasspathLessSource;
import org.sturmm.wicketless.less.source.LessSource;

/**
 * Adapter to less.js parser/compiler using Mozilla's Rhino engine.
 * 
 * @author Martin Sturm
 */
public final class LessParser
{

	private static Logger LOG = LoggerFactory.getLogger(LessParser.class);

	/**
	 * Singleton instance of {@link LessParser}.
	 */
	public static LessParser INSTANCE = new LessParser();

	private Scriptable jsScope;
	private Function parserJs;

	/**
	 * Privat Constructor of {@link LessParser} which initiates a Rhino
	 * javascript context with less.js environment and needed funtions.
	 * 
	 * @throws LessParserInitializationException
	 *             is parser could'nt be initialized.
	 */
	private LessParser()
	{
		try
		{
			LOG.debug("Initializing LessParser ...");
			Context ctx = Context.enter();

			Reader lesscss = new InputStreamReader(
					LessParser.class.getResourceAsStream("less-1.3.0.js"));

			ctx.setOptimizationLevel(1);

			Global global = new Global();
			global.init(ctx);

			jsScope = ctx.initStandardObjects(global);

			Object $logger = Context.javaToJS(LOG, jsScope);
			ScriptableObject.putProperty(jsScope, "log", $logger);

			ctx.evaluateString(jsScope, ScriptFunctions.browser, "browser.js", 1, null);
			ctx.evaluateReader(jsScope, lesscss, "less-1.3.0.js", 1, null);
			ctx.evaluateString(jsScope, ScriptFunctions.importer, "importer.js", 1, null);

			parserJs = ctx.compileFunction(jsScope, ScriptFunctions.parser, "parser.js", 1, null);
			LOG.debug("... parser initialization successfull!");
		}
		catch (Throwable t)
		{
			throw new LessParserInitializationException("Unable to initialize LessCompiler scope.", t);
		}
		finally
		{
			try
			{
				Context.exit();
			}
			catch (Exception e)
			{
				LOG.warn("Exception raised while exiting context.", e);
			}
		}
	}

	/**
	 * Parses a wrapped .less file and returns the {@link Scriptable} object,
	 * which is the AST of the source.
	 * 
	 * @param file
	 *            , wrapper for .less file
	 * @return the AST of {@link ClasspathLessSource} wrapped in
	 *         {@link Scriptable}
	 * @throws LessParsingError
	 *             if parsing of source file fails
	 */
	public final Scriptable parse(LessSource file)
	{
		try
		{
			return (Scriptable)Context
					.call(null, parserJs, jsScope, jsScope, new Object[] { file });
		}
		catch (Exception e)
		{
			throw new LessParsingError("Unable to parse less file " + file.getFilename(), e);
		}
	}

	/**
	 * Returns a singleton instance of {@link LessParser}. Might throw
	 * {@link LessParserInitializationException} if parser could not be initialized.
	 * 
	 * @return parser as singleton
	 */
	public static LessParser getInstance()
	{
		return INSTANCE;
	}


	/*
	 * Helper class for javascript sources. So we dont need to load extra js
	 * file for initialization.
	 */
	private static class ScriptFunctions
	{
		public static final String browser, importer, parser;
		static
		{
			AppendingStringBuffer asb = new AppendingStringBuffer();
			asb.append("var window = {\n");
			asb.append("  location: {\n");
			asb.append("    protocol: 'file',\n");
			asb.append("    href: 'localhost',\n");
			asb.append("    port: '80'\n");
			asb.append("  },\n");
			asb.append("  document: {\n");
			asb.append("    getElementById: function(id){\n");
			asb.append("      return [];\n");
			asb.append("    },\n");
			asb.append("    getElementsByTagName: function(name){\n");
			asb.append("      return [];\n");
			asb.append("    },\n");
			asb.append("  },\n");
			asb.append("  setInterval:function(cb, int) {\n");
			asb.append("    cb.call(this,null);\n");
			asb.append("    return 0;\n");
			asb.append("  }\n");
			asb.append("};\n");
			asb.append("var location    = window.location;\n");
			asb.append("var document    = window.document;\n");
			asb.append("var setInterval = window.setInterval;");
			browser = asb.toString();

			asb = new AppendingStringBuffer();
			asb.append("window.less.Parser.importer = function(path2import, paths, fn, env) {\n");
			asb.append("  var imported = env.rootfile.resolveImport(path2import);\n");
			asb.append("  var ast = imported.getAST();\n");
			asb.append("  var source = imported.getSource();\n");
			asb.append("  fn(null, ast, String(source));\n");
			asb.append("};\n");
			importer = asb.toString();

			asb = new AppendingStringBuffer();
			asb.append("function(lessfile){\n");
			asb.append("  var result;\n");
			asb.append("  new(window.less.Parser)({ filename: String(lessfile.getFilename()), rootfile: lessfile }).parse(String(lessfile.getSource()), function(err, ast){\n");
			asb.append("    if(err) throw err;\n");
			asb.append("    result = ast;\n");
			asb.append("  });\n");
			asb.append("  return result;\n");
			asb.append("};");
			parser = asb.toString();
		}
	}

}
