@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script, version 3.3.2
@REM
@REM Optional ENV vars
@REM -----------------
@REM   JAVA_HOME - location of a JDK home dir, required when download maven via java source
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%MVNW_VERBOSE%"=="true" (
  @ECHO ON
) ELSE (
  @ECHO OFF
)

@REM set local scope for the variables with windows NT shell
@SETLOCAL

@REM ==== START VALIDATION ====
@IF NOT DEFINED JAVA_HOME (
  @ECHO JAVA_HOME not found in your environment. >&2
  @ECHO Please set the JAVA_HOME variable in your environment to match the >&2
  @ECHO location of your Java installation. >&2
  @GOTO error
)

@IF NOT EXIST "%JAVA_HOME%\bin\java.exe" (
  @ECHO ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% >&2
  @ECHO Please set the JAVA_HOME variable in your environment to match the >&2
  @ECHO location of your Java installation. >&2
  @GOTO error
)

@REM ==== END VALIDATION ====

@SET MAVEN_PROJECTBASEDIR=%~dp0
@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@REM set local scope for the variables with windows NT shell
@SETLOCAL ENABLEDELAYEDEXPANSION

@SET ERROR_CODE=0

@REM ==== Read distributionUrl ====
@SET DOWNLOAD_URL=
@FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") DO (
  @IF "%%A"=="distributionUrl" @SET DOWNLOAD_URL=%%B
)

@IF "%DOWNLOAD_URL%"=="" (
  @ECHO ERROR: cannot read distributionUrl property in %MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties >&2
  @GOTO error
)

@REM Extract distribution filename
@FOR %%i IN ("%DOWNLOAD_URL%") DO @SET DOWNLOAD_FILE=%%~nxi

@REM Define Maven home
@IF NOT DEFINED MAVEN_USER_HOME (
  @SET MAVEN_USER_HOME=%USERPROFILE%\.m2
)

@REM Create a hash of the distribution URL for the directory name
@SETLOCAL ENABLEDELAYEDEXPANSION
@SET HASH_VALUE=0
@SET URL_STRING=%DOWNLOAD_URL%
@FOR /L %%i IN (0,1,500) DO (
  @IF NOT "!URL_STRING!"=="" (
    @SET "CHAR=!URL_STRING:~0,1!"
    @SET "URL_STRING=!URL_STRING:~1!"
    @FOR /F "delims=" %%j IN ('powershell -Command "[int][char]'!CHAR!'"') DO (
      @SET /A "HASH_VALUE=((HASH_VALUE * 31) + %%j) %% 2147483647"
    )
  )
)
@SET MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%DOWNLOAD_FILE:~0,-4%\%HASH_VALUE%
@ENDLOCAL & @SET "MAVEN_HOME=%MAVEN_HOME%"

@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  @IF "%MVNW_VERBOSE%"=="true" (
    @ECHO Found Maven at %MAVEN_HOME%
  )
  @GOTO execute
)

@IF "%MVNW_VERBOSE%"=="true" (
  @ECHO Downloading Maven from: %DOWNLOAD_URL%
  @ECHO Installing to: %MAVEN_HOME%
)

@IF NOT EXIST "%MAVEN_HOME%\.." (
  @MKDIR "%MAVEN_HOME%\.."
)

@SET DOWNLOAD_DIR=%MAVEN_HOME%\..\.download
@IF NOT EXIST "%DOWNLOAD_DIR%" (
  @MKDIR "%DOWNLOAD_DIR%"
)

@REM Download using PowerShell
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webClient = New-Object System.Net.WebClient; if ($env:MVNW_USERNAME -and $env:MVNW_PASSWORD) { $webClient.Credentials = New-Object System.Net.NetworkCredential($env:MVNW_USERNAME, $env:MVNW_PASSWORD); } $webClient.DownloadFile('%DOWNLOAD_URL%', '%DOWNLOAD_DIR%\%DOWNLOAD_FILE%'); }" || (
  @ECHO ERROR: Failed to download Maven distribution >&2
  @GOTO error
)

@IF "%MVNW_VERBOSE%"=="true" (
  @ECHO Extracting Maven distribution
)

@REM Extract using PowerShell
powershell -Command "& {Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%DOWNLOAD_DIR%\%DOWNLOAD_FILE%', '%MAVEN_HOME%\..')}" || (
  @ECHO ERROR: Failed to extract Maven distribution >&2
  @GOTO error
)

@REM Clean up
@RMDIR /S /Q "%DOWNLOAD_DIR%"

:execute
@SETLOCAL

@SET MAVEN_CMD_LINE_ARGS=%*

@"%MAVEN_HOME%\bin\mvn.cmd" %MAVEN_CMD_LINE_ARGS%
@SET ERROR_CODE=%ERRORLEVEL%

@ENDLOCAL & @SET ERROR_CODE=%ERROR_CODE%

:error
@ENDLOCAL
@EXIT /B %ERROR_CODE%
