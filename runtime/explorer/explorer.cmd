@echo off
rem Runs Nasdanika Explorer from this module's target directory, after "mvn package".
rem   explorer
for %%f in ("%~dp0target\explorer-*.jar") do java -jar "%%f" %*
