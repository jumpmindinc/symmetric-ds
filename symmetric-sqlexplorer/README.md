# Introduction

This library provides a sql explorer component that can be used in Vaadin applications.  This component is used by the Metl and SymmetricDS Pro applications.

# Usage

[DemoUI.java](src/test/java/org/jumpmind/vaadin/ui/sqlexplorer/DemoUI.java) is an example of how to use the Sql Explorer component.

It can be included via gradle or maven.  The following is an example of how it can be included using gradle.  Including this component will require the vaadin widgetset to be compiled as it includes other addons that require widgetset compilation.
```
repositories {
  mavenCentral()    
  jcenter()        
  maven { url "http://maven.vaadin.com/vaadin-addons" }
}

dependencies {
  compile 'org.jumpmind.symmetric:symmetric-sqlexplorer:3.9.x-SNAPSHOT'
}
```

<p align="center">
  <img src='src/test/screenshots/screenshot2.png?raw=true' />
</p>

<p align="center">
  <img src='src/test/screenshots/screenshot1.png?raw=true' />
</p>


