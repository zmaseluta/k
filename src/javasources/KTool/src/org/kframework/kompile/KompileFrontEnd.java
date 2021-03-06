package org.kframework.kompile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FilenameUtils;
import org.kframework.backend.Backend;
import org.kframework.backend.html.HtmlBackend;
import org.kframework.backend.java.symbolic.JavaSymbolicBackend;
import org.kframework.backend.latex.LatexBackend;
import org.kframework.backend.latex.PdfBackend;
import org.kframework.backend.maude.KompileBackend;
import org.kframework.backend.symbolic.SymbolicBackend;
import org.kframework.backend.unparser.UnparserBackend;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.CompilerSteps;
import org.kframework.compile.utils.MetaK;
import org.kframework.kil.Definition;
import org.kframework.kil.loader.Context;
import org.kframework.kil.loader.CountNodesVisitor;
import org.kframework.krun.Main;
import org.kframework.parser.DefinitionLoader;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.file.KPaths;
import org.kframework.utils.general.GlobalSettings;
import org.kframework.utils.OptionComparator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KompileFrontEnd {

	public static String output;

	private static List<String> metadataParse(String tags) {
		String[] alltags = tags.split("\\s+");
		List<String> result = new ArrayList<String>();
		for (int i = 0; i < alltags.length; i++)
			result.add(alltags[i]);
		return result;
	}

	private static final String USAGE = "kompile [options] <file>" + System.getProperty("line.separator");
	private static final String HEADER_STANDARD = "";
	private static final String FOOTER_STANDARD = "";
	private static final String HEADER_EXPERIMENTAL = "Experimental options:";
	private static final String FOOTER_EXPERIMENTAL = Main.FOOTER_EXPERIMENTAL;
	public static void printUsageS(KompileOptionsParser op) {
		org.kframework.utils.Error.helpMsg(USAGE, HEADER_STANDARD, FOOTER_STANDARD, op.getOptionsStandard(), new OptionComparator(op.getOptionList()));
	}
	public static void printUsageE(KompileOptionsParser op) {
		org.kframework.utils.Error.helpMsg(USAGE, HEADER_EXPERIMENTAL, FOOTER_EXPERIMENTAL, op.getOptionsExperimental(), new OptionComparator(op.getOptionList()));
	}

	public static void kompile(String[] args) {
		KompileOptionsParser op = new KompileOptionsParser();

		CommandLine cmd = op.parse(args);
		if (cmd == null) {
			printUsageS(op);
			System.exit(1);
		}

		// options: help
		if (cmd.hasOption("help")) {
			printUsageS(op);
			System.exit(0);
		}
		if (cmd.hasOption("help-experimental")) {
			printUsageE(op);
			System.exit(0);
		}

		if (cmd.hasOption("version")) {
			String msg = FileUtil.getFileContent(KPaths.getKBase(false) + KPaths.VERSION_FILE);
			System.out.println(msg);
			System.exit(0);
		}

		if (cmd.hasOption("smt"))
			GlobalSettings.NOSMT = cmd.getOptionValue("smt").equals("none");
        
		if (cmd.hasOption("verbose"))
			GlobalSettings.verbose = true;

		if (cmd.hasOption("fast-kast")) {
			GlobalSettings.fastKast = !GlobalSettings.fastKast;
		}

		if (cmd.hasOption("warnings"))
			GlobalSettings.warnings = cmd.getOptionValue("warnings");

		if (cmd.hasOption("transition"))
			GlobalSettings.transition = metadataParse(cmd.getOptionValue("transition"));
		if (cmd.hasOption("supercool"))
			GlobalSettings.supercool = metadataParse(cmd.getOptionValue("supercool"));
		if (cmd.hasOption("superheat"))
			GlobalSettings.superheat = metadataParse(cmd.getOptionValue("superheat"));

		if (cmd.hasOption("doc-style")) {
			String style = cmd.getOptionValue("doc-style");
			if (style.startsWith("+")) {
				GlobalSettings.style += style.replace("+", ",");
			} else {
				GlobalSettings.style = style;
			}
		}

		if (cmd.hasOption("add-top-cell"))
			GlobalSettings.addTopCell = true;

		// set lib if any
		if (cmd.hasOption("lib")) {
			GlobalSettings.lib = cmd.getOptionValue("lib");
		}
		if (cmd.hasOption("syntax-module"))
			GlobalSettings.synModule = cmd.getOptionValue("syntax-module");

		String step = null;
		if (cmd.hasOption("step")) {
			step = cmd.getOptionValue("step");
		}


		String def = null;
		{
			String[] restArgs = cmd.getArgs();
			if (restArgs.length < 1)
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "You have to provide a file in order to compile!.", "command line", "System file."));
			else
				def = restArgs[0];
		}

		File mainFile = new File(def);
		GlobalSettings.mainFile = mainFile;
		GlobalSettings.mainFileWithNoExtension = mainFile.getAbsolutePath().replaceFirst("\\.k$", "").replaceFirst("\\.xml$", "");
		if (!mainFile.exists()) {
			File errorFile = mainFile;
			mainFile = new File(def + ".k");
			if (!mainFile.exists()) {
				String msg = "File: " + errorFile.getName() + "(.k) not found.";
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, errorFile.getAbsolutePath(), "File system."));
			}
		}

		output = null;
		if (cmd.hasOption("directory")) {
			output = cmd.getOptionValue("directory");
			org.kframework.utils.Error.checkIfOutputDirectory(output);
		}
		if (output == null) {
			output = mainFile.getAbsoluteFile().getParent();
		}
		GlobalSettings.outputDir = output;

		String lang = null;
		if (cmd.hasOption("main-module"))
			lang = cmd.getOptionValue("main-module");
		else
			lang = FileUtil.getMainModule(mainFile.getName());

		Context context = new Context();
		if (cmd.hasOption("kcells")) {
			String kCells = cmd.getOptionValue("kcells");
			List<String> komputationCells = new ArrayList<String>();
			for (String s : kCells.split(" ")) {
				komputationCells.add(s);
			}
			context.setKomputationCells(komputationCells);
			assert !context.getKomputationCells().isEmpty();
		}

        context.dotk = new File(output + File.separator + ".k");
        context.dotk.mkdirs();
		
		Backend backend = null;
		String backendOpt;
		if (cmd.hasOption("backend")) {
			backendOpt = cmd.getOptionValue("backend");
		} else {
			backendOpt = "maude";
		}
		switch (backendOpt) {
		case "pdf":
			GlobalSettings.documentation = true;
			backend = new PdfBackend(Stopwatch.sw, context);
			break;
		case "latex":
			GlobalSettings.documentation = true;
			backend = new LatexBackend(Stopwatch.sw, context);
			break;
		case "html":
			if (!cmd.hasOption("doc-style")) {
				GlobalSettings.style = "k-definition.css";
			}
			GlobalSettings.documentation = true;
			backend = new HtmlBackend(Stopwatch.sw, context);
			break;
		case "maude":
			backend = new KompileBackend(Stopwatch.sw, context);
            context.dotk = new File(output + File.separator + FilenameUtils.removeExtension(mainFile.getName()) + "-kompiled");
			checkAnotherKompiled(context.dotk);
			context.dotk.mkdirs();
			break;
		case "java":
			GlobalSettings.javaBackend = true;
			backend = new JavaSymbolicBackend(Stopwatch.sw, context);
            context.dotk = new File(output + File.separator + FilenameUtils.removeExtension(mainFile.getName())
                    + "-kompiled");
			checkAnotherKompiled(context.dotk);
			context.dotk.mkdirs();
			break;
		case "unparse":
			backend = new UnparserBackend(Stopwatch.sw, context);
			break;
		case "unflatten":
			backend = new UnparserBackend(Stopwatch.sw, context, true);
			break;
		case "symbolic":
			GlobalSettings.symbolic = true;
			backend = new SymbolicBackend(Stopwatch.sw, context);
            context.dotk = new File(output + File.separator + FilenameUtils.removeExtension(mainFile.getName()) + "-kompiled");
			checkAnotherKompiled(context.dotk);
			context.dotk.mkdirs();
            if (cmd.hasOption("symbolic-rules")) {
                GlobalSettings.symbolicTags = Arrays.asList(cmd.getOptionValue("symbolic-rules").trim().split("\\s+"));
            }
            if (cmd.hasOption("non-symbolic-rules")) {
                GlobalSettings.nonSymbolicTags = Arrays.asList(cmd.getOptionValue("non-symbolic-rules").trim().split("\\s+"));
            }
            break;
		default:
			GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, "Invalid backend option: " + backendOpt, "", ""));
			break;
		}

		if (backend != null)
			genericCompile(mainFile, lang, backend, step, context);

		verbose(cmd, context);
	}

	private static void verbose(CommandLine cmd, Context context) {
        Stopwatch.sw.printTotal("Total");
		if (GlobalSettings.verbose) {
            context.printStatistics();
        }
		GlobalSettings.kem.print();
		if (cmd.hasOption("loud"))
			System.out.println("Done.");
	}


	private static void genericCompile(
            File mainFile,
            String lang,
            Backend backend,
            String step,
            Context context) {
		org.kframework.kil.Definition javaDef;
		try {
			Stopwatch.sw.start();
			javaDef = DefinitionLoader.loadDefinition(mainFile, lang, backend.autoinclude(), context);
            javaDef.accept(new CountNodesVisitor(context));

			CompilerSteps<Definition> steps = backend.getCompilationSteps();

			if (step == null) {
				step = backend.getDefaultStep();
			}
			try {
				javaDef = steps.compile(javaDef, step);
			} catch (CompilerStepDone e) {
				javaDef = (Definition) e.getResult();
			}

			BinaryLoader.save(
                context.dotk.getAbsolutePath() + "/configuration.bin", MetaK.getConfiguration(javaDef, context)
            );

			backend.run(javaDef);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void checkAnotherKompiled(File kompiled) {
		File[] kompiledList = kompiled.getParentFile().listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File current, String name) {
				File f = new File(current, name);
				return f.isDirectory() && f.getAbsolutePath().endsWith("-kompiled");
			}
		});
		for (int i = 0; i < kompiledList.length; i++) {
			if (!kompiledList[i].getName().equals(kompiled.getName())) {
				String msg = "Creating multiple kompiled definition in the same directory is not allowed.";
				GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, "command line", kompiledList[i].getAbsolutePath()));
			}
		}
	}
}

// vim: noexpandtab
