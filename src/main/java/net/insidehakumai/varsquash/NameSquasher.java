package net.insidehakumai.varsquash;

import com.google.common.collect.HashBiMap;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import javax.management.openmbean.KeyAlreadyExistsException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Javaソースコードに含まれる変数名の短縮化を行います。
 * また，変数の元の名前と短縮化を行った後の名前のマッピングを保持します．
 * TODO Translate in English
 */
public class NameSquasher {

    private SquashNameApproach squashNameApproach;
    private HashBiMap<String, String> patternMap;

    public NameSquasher(SquashFormat format) {
        patternMap = HashBiMap.create();

        if (format == SquashFormat.FIRST_LETTER) {
            squashNameApproach = new SquashNameByFirstLetter(patternMap);
        } else if (format == SquashFormat.DOLLAR) {
            squashNameApproach = new SquashNameByDollar(patternMap);
        }
    }

    /**
     * Javaファイルの中で使用されている変数の名前を短縮し，その結果得られるソースコードをファイルに出力する
     * @param inputFilePath 変数の名前を短縮するJavaファイルのファイルパス
     * @param outputFilePath 変数の名前を短縮して得られるJavaソースコードの出力先ファイルパス
     * @throws FileNotFoundException 入力するJavaファイルが存在しなかった場合
     * TODO Translate in English
     */
    public void squashNamesInFile(String inputFilePath, String outputFilePath) throws IOException {

        ASTParser parser = ASTParser.newParser(AST.JLS11);
        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_11, options);
        parser.setCompilerOptions(options);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setSource(readAll(inputFilePath).toCharArray());
        parser.setEnvironment(new String[] {}, new String[] {}, null, true);

        parser.createASTs(
            new String[]{inputFilePath},
            null,
            new String[] {},
            new FileASTRequestor() {
                @Override
                public void acceptAST(String sourceFilePath, CompilationUnit cu) {

                    for (Object typeDec : cu.types()) {
                        ((TypeDeclaration) typeDec).accept(new MyVisitor(cu, squashNameApproach));
                    }

                    for (Object typeDec : cu.types()) {
                        ((TypeDeclaration) typeDec).accept(new ASTVisitor() {
                            @Override
                            public boolean visit(SimpleName node) {
                                if (node.resolveBinding() != null) {
                                    String squashedName = patternMap.get(node.resolveBinding().getKey());
                                    if (squashedName != null) {
                                        node.setIdentifier(squashedName);
                                    }
                                }
                                return super.visit(node);
                            }
                        });
                    }

                    super.acceptAST(sourceFilePath, cu);

                    try {
                        // TODO Ensure directory existence
                        File file = new File(outputFilePath);
                        FileWriter fileWriter = new FileWriter(file);
                        fileWriter.write(cu.toString());
                        fileWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            },
            new NullProgressMonitor()
        );
    }

    public static String readAll(final String path) throws IOException {
        return Files.lines(Paths.get(path), Charset.forName("UTF-8"))
            .collect(Collectors.joining(System.getProperty("line.separator")));
    }

}

class MyVisitor extends ASTVisitor {

    private CompilationUnit accepteeCompilationUnit;
    private SquashNameApproach squashNameApproach;

    MyVisitor(CompilationUnit accepteeCompilationUnit, SquashNameApproach squashNameApproach) {
        super();
        this.squashNameApproach = squashNameApproach;
        this.accepteeCompilationUnit = accepteeCompilationUnit;
    }

    @Override
    public boolean visit(FieldDeclaration fieldDec) {
        fieldDec.accept(new VariableDeclarationFragmentVisitor(squashNameApproach));
        return super.visit(fieldDec);
    }

    @Override
    public boolean visit(VariableDeclarationStatement valDecStmt) {
        valDecStmt.accept(new VariableDeclarationFragmentVisitor(squashNameApproach));
        return super.visit(valDecStmt);
    }

    @Override
    public boolean visit(SingleVariableDeclaration valDec) {

        // TODO VariableDeclarationFragmentVisitorと内容が重複しているからまとめられるか検討
        SimpleName variableName = valDec.getName();

        if (variableName.resolveBinding() != null) {
            String bindingKey = variableName.resolveBinding().getKey();

            String squashedName = squashNameApproach.squashName(bindingKey, variableName.toString());
            // System.out.println(String.format("%3d %-40s --> %-10s %s",
            //     accepteeCompilationUnit.getLineNumber(valDec.getStartPosition()),
            //     variableName,
            //     squashedName,
            //     bindingKey)
            // );
            valDec.setName(valDec.getAST().newSimpleName(squashedName));

            valDec.accept(new VariableDeclarationFragmentVisitor(squashNameApproach));
        }
        return super.visit(valDec);
    }


    private class VariableDeclarationFragmentVisitor extends ASTVisitor {

        private SquashNameApproach squashNameApproach;

        VariableDeclarationFragmentVisitor(SquashNameApproach squashNameApproach) {
            super();
            this.squashNameApproach = squashNameApproach;
        }

        @Override
        public boolean visit(VariableDeclarationFragment valDecFragment) {

            SimpleName variableName = valDecFragment.getName();

            if (variableName.resolveBinding() != null) {
                String bindingKey = variableName.resolveBinding().getKey();

                String squashedName = squashNameApproach.squashName(bindingKey, variableName.toString());
                // System.out.println(String.format("%3d %-40s --> %-10s %s",
                //     accepteeCompilationUnit.getLineNumber(valDecFragment.getStartPosition()),
                //     variableName,
                //     squashedName,
                //     bindingKey)
                // );

                valDecFragment.setName(valDecFragment.getAST().newSimpleName(squashedName));
            }
            return super.visit(valDecFragment);
        }

    }

}

abstract class SquashNameApproach {

    protected HashBiMap<String, String> patternMap;

    SquashNameApproach(HashBiMap<String, String> patternMap) {
        this.patternMap = patternMap;
    }

    abstract String squashName(String key, String originalName);
}


class SquashNameByFirstLetter extends SquashNameApproach {

    SquashNameByFirstLetter(HashBiMap<String, String> patternMap) {
        super(patternMap);
    }

    @Override
    String squashName(String key, String originalName) {

        if (patternMap.containsKey(key)) {
            throw new KeyAlreadyExistsException(String.format("Squash pattern for %s is already exists (%s)", key, patternMap.get(key)));
        }

        String squashedName;

        int charIndex = 0;
        do {
            charIndex++;
            if (charIndex < originalName.length()) {
                squashedName = originalName.substring(0, charIndex);
            } else {
                squashedName = originalName + "_".repeat(charIndex - originalName.length());
            }
        } while (patternMap.containsValue(squashedName));

        patternMap.put(key, squashedName);
        return squashedName;
    }

}


class SquashNameByDollar extends SquashNameApproach {

    SquashNameByDollar(HashBiMap<String, String> patternMap) {
        super(patternMap);
    }

    @Override
    String squashName(String key, String originalName) {

        if (patternMap.containsKey(key)) {
            throw new KeyAlreadyExistsException(String.format("Squash pattern for %s is already exists (%s)", key, patternMap.get(key)));
        }

        String squashedName;

        int nameIndex = 0;
        while (patternMap.containsValue("$" + nameIndex)) {
            nameIndex++;
        }
        squashedName = "$" + nameIndex;

        patternMap.put(key, squashedName);
        return squashedName;
    }

}
