@echo off
rem Runs Nasdanika Commander from this module's target directory, after "mvn package".
rem   commander [left directory] [right directory]
for %%f in ("%~dp0target\commander-*.jar") do java -jar "%%f" %*
