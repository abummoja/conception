package org.jewelsea.conception;

import com.sun.deploy.Environment;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EventListener;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.scene.layout.VBoxBuilder;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * A CodeMirror based JavaScript CodeEditor wrapped in a JavaFX WebView, with
 * the ability to compile and run the edited code.
 */
public class Conception extends Application {

    private final static Logger log = Logger.getLogger(Conception.class.getName());

    private static final String MAIN_CLASS_NAME = "HelloWorld";

    // some sample code to be edited.
    private static final String editingCode
            = "public class HelloWorld {\n"
            + "  public static void main(String[] args) {\n"
            + "    System.out.println(\"Hello, World\");\n"
            + "  }\n"
            + "}\n";

    private static final String SOURCE_FILE_TYPE = ".java";
    private static final String TEMP_DIRECTORY_NAME = "concept-";

    public static void main(String[] args) {
        launch(args);
    }

    private JavaSourceCompiler compiler;
    private LogArea logArea;
    private Path compilationDir;

    @Override
    public void init() throws Exception {
        compiler = new JavaSourceCompiler();
    }

    @Override
    public void stop() throws Exception {
        FileHelper.recursivelyDeleteDir(compilationDir);
    }

    void updateLogArea(String str) {
        logArea.setText(logArea.getText() + "\n" + str);
        //todo: scroll to latest insertion
    }

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Conception");
        stage.getIcons().setAll(
                new Image(getResourceLoc("icons/flower-seed-icon-16.png")),
                new Image(getResourceLoc("icons/flower-seed-icon-32.png")),
                new Image(getResourceLoc("icons/flower-seed-icon-64.png")),
                new Image(getResourceLoc("icons/flower-seed-icon-128.png")),
                new Image(getResourceLoc("icons/flower-seed-icon-256.png")),
                new Image(getResourceLoc("icons/flower-seed-icon-512.png"))
        );

        // create the editing controls.
        Label title = new Label("Editing: " + MAIN_CLASS_NAME + SOURCE_FILE_TYPE);
        title.setStyle("-fx-font-size: 20;");
        final CodeEditor editor = new CodeEditor(editingCode);
        editor.autosize();
        Button openBtn = new Button();
        openBtn.setText("Open");
        openBtn.setTooltip(new Tooltip("Opens a .java file."));
        openBtn.setOnAction(new EventHandler<ActionEvent>() {
            //private FileChooser.ExtensionFilter filter;

            @Override
            public void handle(ActionEvent event) {
                FileChooser filePicker = new FileChooser();
                filePicker.setTitle("Select A Java File");
                /*filter.getExtensions().add(".java");
                filter.getExtensions().add("*.java");
                filter.getExtensions().add(".kt");*/
                //filePicker.setSelectedExtensionFilter(filter);
                filePicker.setInitialDirectory(new File(System.getProperty("user.home")));
                File theJavaFile = filePicker.showOpenDialog(title.getScene().getWindow());
                if (theJavaFile != null && (theJavaFile.getPath().endsWith(".java") || theJavaFile.getPath().endsWith(".kt"))) {
                    //open file in editor
                    String fileContent = "";
                    try {
                        Scanner scanner = new Scanner(new File(theJavaFile.getPath()));
                        while (scanner.hasNextLine()) {
                            String line = scanner.nextLine();
                            fileContent = fileContent + "\n" + line;
                            //add
                        }
                        updateLogArea("Opened File: " + theJavaFile.getAbsolutePath());
                        editor.setCode(fileContent);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        updateLogArea("Error Opening File: " + theJavaFile.getAbsolutePath());
                        updateLogArea(e.getMessage());
                    }

                } else {
                    updateLogArea("Please select a Java File (ends with .java)");
                }
                //throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }
        });
        logArea = new LogArea();

        // display the scene.
        stage.setScene(new Scene(
                layoutScene(
                        title,
                        openBtn,
                        editor,
                        createRunButton(editor),
                        logArea
                )//.getChildren().add(new Button openBtn)

        ));

        stage.show();
    }

    private Button createRunButton(final CodeEditor editor) {
        final Button run = new Button("Run");
        run.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                compileAndRunCode(editor);
            }
        });

        if (!compiler.isInitialized()) {
            run.setDisable(true);
            updateLogArea("Compiler not initialized...\nAborting...");
            //logArea.setText(logArea.getText() + "\nCompiler not initialized...\nAborting...");
        }

        return run;
    }

    private void compileAndRunCode(final CodeEditor editor) {
        logArea.captureOutput();
        String code = null;
        try {
            code = editor.getCodeAndSnapshot();
        } catch (Exception ex) {
            updateLogArea(ex.getMessage());
            //logArea.setText(logArea.getText() + "\n" + ex.getMessage());
            System.out.println(ex.getMessage());
            // if the editor wasn't ready loading code, we may get an exception
            // if this happens we just ignore it and don't try to compile and run the code;
            return;
        }
        if (code != null) {
            try {
                FileHelper.recursivelyDeleteDir(compilationDir);
                compilationDir = Files.createTempDirectory(TEMP_DIRECTORY_NAME);
                compiler.setLocation(compilationDir.toFile());
                Path sourceFile = compilationDir.resolve(MAIN_CLASS_NAME + SOURCE_FILE_TYPE);
                Files.write(sourceFile, code.getBytes());

                boolean compiled = compile(sourceFile.toFile());
                if (compiled) {
                    runCompiledCode();
                    logArea.setText("Compiler Success!");
                }
            } catch (IOException ex) {
                log.log(Level.SEVERE, "Unable to create temporary storage for compilation", ex);
                logArea.setText("ERROR: " + ex.getMessage());
            }
        }
    }

    private boolean compile(String code) {
        return compiler.compile(MAIN_CLASS_NAME, code);
    }

    private boolean compile(File file) {
        return compiler.compile(MAIN_CLASS_NAME, file);
    }

    private void runCompiledCode() {
        URL compiledCodeLoc = compiler.getCompiledCodeLocation(MAIN_CLASS_NAME);
        JavaCodeExecutor executor = new JavaCodeExecutor(compiledCodeLoc);
        executor.execute(MAIN_CLASS_NAME);
    }

    private VBox layoutScene(Node... nodes) {
        final VBox layout = VBoxBuilder.create().spacing(10).children(nodes).build();
        layout.setStyle("-fx-background-color: cornsilk; -fx-padding: 10;");
        return layout;
    }

    private String getResourceLoc(String name) {
        return getClass().getResource("resources/" + name).toExternalForm();
    }
}
