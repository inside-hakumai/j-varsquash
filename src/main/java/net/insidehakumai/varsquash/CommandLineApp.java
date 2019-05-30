package net.insidehakumai.varsquash;

import org.apache.commons.cli.*;

import java.io.FileNotFoundException;
import java.io.IOException;

import static java.lang.System.exit;

/**
 * Entry point class for executing from command line
 */
public class CommandLineApp {

    public static void main(String[] args) {

        /*
         * Process command line arguments
         */
        Options options = new Options();

        Option trainDirOpt = new Option("i", "infile", true, "path of file to be deranged");
        trainDirOpt.setRequired(true);
        options.addOption(trainDirOpt);

        Option outputDirOpt = new Option("o", "outfile", true, "path to result file"); // TODO ちゃんと書く
        outputDirOpt.setRequired(true);
        options.addOption(outputDirOpt);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("options", options);

            exit(1);
            return;
        }

        try {
            new NameSquasher(SquashFormat.FIRST_LETTER).squashNamesInFile(cmd.getOptionValue("infile"), cmd.getOptionValue("outfile"));
        } catch (IOException e) {
            e.printStackTrace();
            exit(1);
        }

    }
}
