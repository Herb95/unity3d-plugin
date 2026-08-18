@echo off
rem Build this plugin with a supported JDK (17-21) instead of the machine default.
rem
rem The build must run Maven itself on JDK 17-21: the parent's license-maven-plugin and
rem spotbugs-maven-plugin cannot read class files newer than Java 21 ("Unsupported class
rem file major version ..."), so a default JAVA_HOME of JDK 22+ makes `mvn verify` fail.
rem
rem JDK resolution order:
rem   1. %%UNITY3D_JAVA_HOME%%, if set
rem   2. the path in .mvn\java-home (git-ignored, machine-local), if present
rem   3. otherwise Maven runs with the existing JAVA_HOME / PATH as-is
rem
rem Usage: mvn21.cmd <usual mvn args>   e.g.  mvn21.cmd verify

setlocal
if not defined UNITY3D_JAVA_HOME if exist "%~dp0.mvn\java-home" set /p UNITY3D_JAVA_HOME=<"%~dp0.mvn\java-home"
if defined UNITY3D_JAVA_HOME set "JAVA_HOME=%UNITY3D_JAVA_HOME%"
mvn %*
exit /b %ERRORLEVEL%
