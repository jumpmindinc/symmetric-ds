Introduction
------------
This project is used to publish artifacts to Sonatype's Maven Central repository.
It's needed because some drivers, libraries, or tools are not available in Maven,
but they need to be so they can be accessed by the module manager.

Eclipse
-------
If you want to work in Eclipse with this project, the run:

../symmetric-assemble/gradlew eclipse

Then use File -> Import -> Existing Projects into Workspace.

Adding a Module
---------------
The "publications" section of the "build.gradle" needs an entry for the module.

    mymodule(MavenPublication) {
        artifact "build/assemble/mymodule-1.0.jar"
        groupId "org.jumpmind.symmetric.module"; artifactId "mymodule"; version "1.0"
        artifact tasks.named('sourcesJar'); artifact tasks.named('javadocJar')
    }
        
Place the module file ("mymodule-1.0.jar") into one of "jdbc", "lib", or "tools"
sub-directory.  These don't need to be checked into the code repository, but
checking in the changes to build.gradle will show active modules in use.

Configure Properties
--------------------
The following Gradle properties need set, which are also found in "gradle.properties".

signingKey=This is the GPG key text block that starts with BEGIN PGP PRIVATE KEY BLOCK
signingPassword=The GPG key is encrypted with this password
mavenCentralPortalUsername=This is the username from an active user token
mavenCentralPortalPassword=This is the password from an active user token

When using "gradle.properties", the multi-line GPG key should be on one line with
newlines shown as \n.

Test Publishing
---------------
Publish to the local repository as a test.  There is a publish task that is 
automatically available for your publication that can be called.
        
../symmetric-assemble/gradlew clean jar publishMymoduleToProjectLocalRepository

The results are found in "build/project-local-repository".

Publish to Maven
----------------
When ready, publish the module to Maven.

../symmetric-assemble/gradlew clean jar publishMymoduleToProjectLocalRepository releaseMavenCentralPortalPublication

It can take anywhere from 10 minutes to an hour or more for the publication to be available.
Login to the Sonatype Portal to look at status:

    https://central.sonatype.com/

After its published, you can see it in the repository:

    https://repo1.maven.org/maven2/org/jumpmind/symmetric/module/mymodule/

