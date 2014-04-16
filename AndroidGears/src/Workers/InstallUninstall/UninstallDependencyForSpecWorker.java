package Workers.InstallUninstall;

import Models.GearSpec.GearSpec;
import Models.GearSpec.GearSpecDependency;
import Utilities.GearSpecRegistrar;
import Utilities.Utils;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by matthewyork on 4/4/14.
 */
public class UninstallDependencyForSpecWorker extends SwingWorker<Void, Void> {

    private ArrayList<GearSpec> selectedSpecs;
    private Project project;
    private Module module;
    public boolean successful;

    public UninstallDependencyForSpecWorker(ArrayList<GearSpec> selectedSpecs, Project project, Module module) {
        this.selectedSpecs = selectedSpecs;
        this.project = project;
        this.module = module;
    }

    @Override
    protected Void doInBackground() throws Exception {

        for (GearSpec selectedSpec : this.selectedSpecs){
            if (selectedSpec.getType().equals(GearSpec.SPEC_TYPE_JAR)){
                if (!uninstallJar(selectedSpec, this.project)){
                    successful = false;
                    return null;
                }
            }
            else if (selectedSpec.getType().equals(GearSpec.SPEC_TYPE_MODULE)){
                if (!uninstallModule(selectedSpec, this.project)){
                    successful = false;
                    return null;
                }
            }
        }


        successful = true;
        return null;
    }

    private Boolean uninstallJar(GearSpec spec, Project project){
        //Make local path separator for speed
        String pathSeparator = Utils.pathSeparator();

        //Get the gears jar directory. If it doesn't exist, then we will count that as a win
        File libsDirectory = new File(project.getBasePath()+ pathSeparator + "Gears"+ pathSeparator + "Jars"+ pathSeparator + spec.getName());
        if (!libsDirectory.exists()){
            //Unregister just in case
            if (GearSpecRegistrar.unregisterGear(spec, project)){
                return true;
            }
            else {
                return false;
            }
        }

        //Get the jar file
        File jarFile = new File(libsDirectory.getAbsolutePath()+pathSeparator+spec.getVersion()+pathSeparator+Utils.jarFileNameForSpecSource(spec.getSource()));

        //Delete the jar
        if (jarFile.exists()){
            try {
                FileUtils.forceDelete(libsDirectory);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        //Update settings files
        if (!updateProjectSettingsForJar(spec)){
            return false;
        }

        //Finally, unregister gear
        if (GearSpecRegistrar.unregisterGear(spec, project)){
            return true;
        }
        else {
            return false;
        }
    }

    private Boolean uninstallModule(GearSpec spec, Project project){
        //Make local path separator for speed
        String pathSeparator = Utils.pathSeparator();

        File libsDirectory = new File(project.getBasePath()+ pathSeparator+ "Gears"+ pathSeparator + "Modules");
        if (!libsDirectory.exists()){
            //Unregister just in case
            if (GearSpecRegistrar.unregisterGear(spec, project)){
                return true;
            }
            else {
                return false;
            }
        }

        //Get the jar file
        File moduleDirectory = new File(libsDirectory.getAbsolutePath()+pathSeparator+spec.getName());

        //Delete the jar
        if (moduleDirectory.exists()){
            try {
                FileUtils.deleteDirectory(moduleDirectory);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        //Update settings files
        if (!updateProjectSettingsForModule(spec)){
            return false;
        }

        //Finally, unregister gear
        if (GearSpecRegistrar.unregisterGear(spec, project)){
            return true;
        }
        else {
            return false;
        }
    }

    private Boolean updateProjectSettingsForJar(GearSpec spec){
        //Make local path separator for speed
        String pathSeparator = Utils.pathSeparator();

        File buildFile = new File(new File(module.getModuleFilePath()).getParentFile().getAbsolutePath() + pathSeparator + "build.gradle");

        //Modify build file
        if (buildFile.exists()){
            try {
                File libsDirectory = new File(project.getBasePath() + pathSeparator + "Gears"+ pathSeparator + "Jars");

                if(libsDirectory.exists()){
                    //Check to see if all jars are gone. If so, remove the gears jar folder dependency
                    File[] jars = libsDirectory.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File file, String fileName) {
                            if(fileName.contains(".jar")){
                                return true;
                            }
                            else {
                                return false;
                            }
                        }
                    });

                    //No jars, so remove it!
                    if (jars.length == 0){
                        //Read the build file
                        String buildFileString = FileUtils.readFileToString(buildFile);

                        //Create new addition
                        String dependencyString = "dependencies{compile fileTree(dir: '../Gears/Jars/"+spec.getName()+"/"+spec.getVersion()+"', include: ['*.jar'])}";

                        if (buildFileString.contains(dependencyString)){
                            buildFileString = buildFileString.replace(dependencyString, "");
                        }

                        //Write changes to settings.gradle
                        FileUtils.forceDelete(buildFile);
                        FileUtils.write(buildFile, buildFileString);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        else{
            return false;
        }

        return true;
    }

    private Boolean updateProjectSettingsForModule(GearSpec spec){
        //Make local path separator for speed
        String pathSeparator = Utils.pathSeparator();

        //Install dependency and sub-dependencies
        File settingsFile = new File(project.getBasePath() + pathSeparator + "settings.gradle");
        File buildFile = new File(new File(module.getModuleFilePath()).getParentFile().getAbsolutePath() +pathSeparator + "build.gradle");
        File modulesFile = new File(project.getBasePath() + pathSeparator + ".idea"+ pathSeparator +"modules.xml");

        //Modify settings file
        if (settingsFile.exists()){
            try {
                //Read in settings file
                String settingsFileString = FileUtils.readFileToString(settingsFile);

                //Make comparator strings
                String fullLineInclude = "include ':Gears:Modules:"+spec.getName()+":"+spec.getVersion()+"'";
                String partialInclude = "':Gears:Modules:"+spec.getName()+":"+spec.getVersion()+"'";

                //Look for full line inclusion
                if (settingsFileString.contains(fullLineInclude)){
                    settingsFileString = settingsFileString.replace(fullLineInclude, "");
                }
                //Look for partial line inclusions
                else if (settingsFileString.contains(partialInclude+",")){ //Comma after
                    settingsFileString = settingsFileString.replace(partialInclude+",", "");
                }
                else if (settingsFileString.contains(","+partialInclude)){ //Comma before
                    settingsFileString = settingsFileString.replace(","+partialInclude, "");
                }
                else if (settingsFileString.contains(", "+partialInclude)){ //Comma before w/ space
                    settingsFileString = settingsFileString.replace(", "+partialInclude, "");
                }

                //Write changes to settings.gradle
                FileUtils.forceDelete(settingsFile);
                FileUtils.write(settingsFile, settingsFileString);

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        else {
            return false;
        }

        //Modify build file
        if (buildFile.exists()){
            try {
                //Read the build file
                String buildFileString = FileUtils.readFileToString(buildFile);

                //Create new addition
                String dependencyString = "dependencies{compile project (':Gears:Modules:"+spec.getName()+":"+spec.getVersion()+"')}";

                if (buildFileString.contains(dependencyString)){
                    buildFileString = buildFileString.replace(dependencyString, "");
                }

                //Write changes to settings.gradle
                FileUtils.forceDelete(buildFile);
                FileUtils.write(buildFile, buildFileString);

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        else{
            return false;
        }

        //Recreate version entry from the modules.xml
       String versionModuleEntry = "<module fileurl=\"file://$PROJECT_DIR$/Gears/Modules/"+spec.getName()+"/"+spec.getVersion()+"/"+spec.getVersion()+".iml\" filepath=\"$PROJECT_DIR$/Gears/Modules/"+spec.getName()+"/"+spec.getVersion()+"/"+spec.getVersion()+".iml\" />";
        //Recreate gear entry from modles.xml
        String parentModuleEntry = "<module fileurl=\"file://$PROJECT_DIR$/Gears/Modules/"+spec.getName()+"/"+spec.getName()+".iml\" filepath=\"$PROJECT_DIR$/Gears/Modules/"+spec.getName()+"/"+spec.getName()+".iml\" />";

        if (modulesFile.exists()){
            //Read the build file
            try {
                String modulesFileString = FileUtils.readFileToString(modulesFile);

                if (modulesFileString.contains(versionModuleEntry)){
                    modulesFileString = modulesFileString.replace(versionModuleEntry, "");
                }
                if (modulesFileString.contains(parentModuleEntry)){
                    modulesFileString = modulesFileString.replace(parentModuleEntry, "");
                }

                //Write changes to settings.gradle
                FileUtils.forceDelete(modulesFile);
                FileUtils.write(modulesFile, modulesFileString);

            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

        }

        return true;
    }
}
