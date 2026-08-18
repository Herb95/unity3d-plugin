package org.jenkinsci.plugins.unity3d;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.unity3d.io.PipeFileAfterModificationAction;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Represents a Unity3d installation (name, home_dir, etc.)
 *
 * @author Jerome Lacoste
 */
public class Unity3dInstallation extends ToolInstallation
        implements EnvironmentSpecific<Unity3dInstallation>, NodeSpecific<Unity3dInstallation> {

    private static final Logger log = Logger.getLogger(Unity3dInstallation.class.getName());

    enum EditorType {
        UNITY,
        TUANJIE
    }

    enum Platform {
        WINDOWS,
        MAC,
        LINUX
    }

    @DataBoundConstructor
    public Unity3dInstallation(final String name, final String home, final List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public Unity3dInstallation(
            final Unity3dInstallation source, final String home, final List<? extends ToolProperty<?>> properties) {
        super(source.getName(), home, properties);
    }

    public Unity3dInstallation forEnvironment(EnvVars env) {
        return new Unity3dInstallation(
                this, env.expand(getHome()), getProperties().toList());
    }

    public Unity3dInstallation forNode(@NonNull Node node, TaskListener log) throws IOException, InterruptedException {
        return new Unity3dInstallation(
                this, translateFor(node, log), getProperties().toList());
    }

    /**
     * Gets the executable path of this Unity3dBuilder on the given target system.
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public String getExecutable(Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            public String call() throws IOException {
                return checkUnity3dExecutablePath(getHome());
            }
        });
    }

    static class Unity3dExecutablePath {
        String home;
        String path;
        boolean exists;
        EditorType editorType;

        Unity3dExecutablePath(String home, String path, boolean exists, EditorType editorType) {
            this.home = home;
            this.path = path;
            this.exists = exists;
            this.editorType = editorType;
        }

        // Detection priority. TUANJIE is probed before UNITY because a Tuanjie installation also
        // ships a Unity.exe copy for command-line compatibility, so the presence of Tuanjie.exe is
        // the only reliable indicator of a Tuanjie editor: probing for Unity.exe first would
        // misdetect every Tuanjie installation as Unity.
        private static final EditorType[] DETECTION_ORDER = {EditorType.TUANJIE, EditorType.UNITY};

        static Unity3dExecutablePath check(String home) {
            File value = new File(home);
            Platform platform = getPlatform();

            for (EditorType editorType : DETECTION_ORDER) {
                File editorExecutable = getExeFile(value, editorType, platform);
                log.fine("home " + home + " value " + value + " exe " + editorExecutable + " path abs "
                        + editorExecutable.getAbsolutePath() + " path " + editorExecutable.getPath());
                if (value.isDirectory() && editorExecutable.exists()) {
                    return new Unity3dExecutablePath(home, editorExecutable.getPath(), true, editorType);
                }
            }

            File defaultExecutable = getExeFile(value, EditorType.UNITY, platform);
            return new Unity3dExecutablePath(home, defaultExecutable.getPath(), false, EditorType.UNITY);
        }

        boolean isVariableExpanded() {
            return !home.contains("$");
        }

        private static File getExeFile(File editorHome, EditorType editorType, Platform platform) {
            return new File(editorHome, getExecutableRelativePath(editorType, platform));
        }

        public String getInvalidInstallMessage() {
            return Messages.Unity3d_InvalidUnityHomeConfiguration(new File(home), path);
        }

        public String getParametrizedInstallMessage() {
            return Messages.Unity3d_UnityHomeNotFullyExpanded(path);
        }
    }

    static Platform getPlatform() {
        if (Functions.isWindows()) {
            return Platform.WINDOWS;
        } else if (Functions2.isMac()) {
            return Platform.MAC;
        } else {
            return Platform.LINUX;
        }
    }

    static String getExecutableRelativePath(EditorType editorType, Platform platform) {
        String executableName = editorType == EditorType.TUANJIE ? "Tuanjie" : "Unity";
        if (platform == Platform.WINDOWS) {
            return "Editor/" + executableName + ".exe";
        } else if (platform == Platform.MAC) {
            return "Contents/MacOS/" + executableName;
        } else {
            return "Editor/" + executableName;
        }
    }

    private static String checkUnity3dExecutablePath(String home) {
        Unity3dExecutablePath install = Unity3dExecutablePath.check(home);
        if (!install.exists) {
            throw new RuntimeException(install.getInvalidInstallMessage());
        }
        return install.path;
    }

    /**
     * Create a long running task that pipes the Unity3d editor.log into the specified pipe.
     * <p>
     * This future can be {@link Future#cancel(boolean) cancelled} in order for the pipe to be closed properly.
     * @param launcher
     * @param ros the output stream to write into
     * @return the number of bytes read
     * @throws IOException
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public Future<Long> pipeEditorLog(final Launcher launcher, final String customLogFile, final OutputStream ros)
            throws IOException {
        return launcher.getChannel().callAsync(new MasterToSlaveCallable<Long, IOException>() {
            public Long call() throws IOException {
                return new PipeFileAfterModificationAction(
                                getEditorLogFile(customLogFile).getAbsolutePath(), ros, true)
                        .call();
            }
        });
    }

    /**
     * Returns the Editor.log path on the remote machine
     * @param launcher
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public String getEditorLogPath(final Launcher launcher, final String customLogFile)
            throws IOException, InterruptedException {
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            public String call() throws IOException {
                return getEditorLogFile(customLogFile).getAbsolutePath();
            }
        });
    }

    private File getEditorLogFile(String customLogFile) {
        if (customLogFile != null) return new File(customLogFile);

        Platform platform = getPlatform();
        Unity3dExecutablePath executablePath = Unity3dExecutablePath.check(getHome());
        EditorType editorType = executablePath.exists ? executablePath.editorType : EditorType.UNITY;

        if (platform == Platform.WINDOWS) {
            String localAppData;
            try {
                localAppData = Win32Util.getLocalAppData();
                log.fine("Found %LOCALAPPDATA% under " + localAppData);
            } catch (RuntimeException re) {
                log.warning("Unable to find %LOCALAPPDATA%, reverting to Environment variable " + re.getMessage());
                // JENKINS-24265 / providing fallback to LOCALAPPDATA shouldn't be necessary, but this worked most
                // of the cases and I am unable to test for all Windows configurations right now.
                // This should be removed someday....
                localAppData = EnvVars.masterEnvVars.get("LOCALAPPDATA");
                log.fine("Found %LOCALAPPDATA% (from environment variable) under " + localAppData);
                if (localAppData == null) {
                    throw new RuntimeException(
                            "Empty LOCALAPPDATA environment variable. Use -logFile command line argument as workaround. Unable to find Editor.log location (see JENKINS-24265).");
                }
            }
            File applocaldata = new File(localAppData);
            return new File(applocaldata, getEditorLogRelativePath(editorType, platform));
        } else if (platform == Platform.MAC) {
            File userhome = new File(EnvVars.masterEnvVars.get("HOME"));
            return new File(userhome, getEditorLogRelativePath(editorType, platform));
        } else { // Linux assumed
            File userhome = new File(EnvVars.masterEnvVars.get("HOME"));
            return new File(userhome, getEditorLogRelativePath(editorType, platform));
        }
    }

    static String getEditorLogRelativePath(EditorType editorType, Platform platform) {
        String editorDirectory = editorType == EditorType.TUANJIE ? "Tuanjie" : "Unity";
        if (platform == Platform.WINDOWS) {
            return editorDirectory + "/Editor/Editor.log";
        } else if (platform == Platform.MAC) {
            return "Library/Logs/" + editorDirectory + "/Editor.log";
        } else if (editorType == EditorType.TUANJIE) {
            return ".config/tuanjie/Editor.log";
        } else {
            return ".config/unity3d/Editor.log";
        }
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<Unity3dInstallation> {

        @Override
        @NonNull
        public String getDisplayName() {
            return "Unity3d";
        }

        // for compatibility reasons, the persistence is done by Unity3dBuilder.DescriptorImpl
        @Override
        public Unity3dInstallation[] getInstallations() {
            return Jenkins.get()
                    .getDescriptorByType(Unity3dBuilder.DescriptorImpl.class)
                    .getInstallations();
        }

        @Override
        public void setInstallations(Unity3dInstallation... installations) {
            Jenkins.get()
                    .getDescriptorByType(Unity3dBuilder.DescriptorImpl.class)
                    .setInstallations(installations);
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.emptyList();
        }

        /**
         * Checks if the UNITY_HOME is valid.
         */
        public FormValidation doCheckHome(@QueryParameter String value) {
            // this can be used to check the existence of a file on the server, so needs to be protected
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) return FormValidation.ok();

            if (value.isEmpty()) return FormValidation.ok();

            String unityHome = Util.replaceMacro(value, EnvVars.masterEnvVars);
            log.fine("UNITY_HOME:" + unityHome);
            Unity3dExecutablePath install = Unity3dExecutablePath.check(unityHome);

            if (!install.isVariableExpanded()) {
                return FormValidation.ok(install.getParametrizedInstallMessage());
            } else if (!install.exists) {
                return FormValidation.error(install.getInvalidInstallMessage());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
    }
}
