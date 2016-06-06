This is based on the uima-addons project (https://github.com/apache/uima-addons). However, all modules except for the ConceptMapper have been removed as they were out of score.

Instead, only the ConceptMapper module has been kept and modified so that:

* it is compliant with UIMAfit (2.2.0)
* all dependencies to the uima-addons parent pom have been removed so it can be used as a standalone project
* it is completely based on SLF4J for logging
* dictionaries can now be read from files and resources in the classpath (the latter allows to include dictionaries in src/*/resources and be found)
