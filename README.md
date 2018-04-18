# javaagent-instrumentation

A small library, which also to instrument every method in the whitelisted class.\
It will print to stdout method names, which takes more than 50 milliseconds to run.

1. To compile the jar:\
`mvn package`

1. To instrument the application add the following JVM option:\
`-javaagent:java-instrumentation-1.0-SNAPSHOT.jar=classes.cfg`

1. Example of classes.cfg - every classname should be on a separate line\
`com.fasterxml.jackson.databind.ObjectReader`\
`com.fasterxml.jackson.databind.DeserializationContext`\
`com.fasterxml.jackson.databind.deser.DeserializerCache`\
`com.fasterxml.jackson.databind.deser.BeanDeserializerFactory`

1. Sample output:\
`com.fasterxml.jackson.databind.DeserializationContext.findNonContextualValueDeserializer(com.fasterxml.jackson.databind.JavaType) : 94
 com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCache2(com.fasterxml.jackson.databind.DeserializationContext,com.fasterxml.jackson.databind.deser.DeserializerFactory,com.fasterxml.jackson.databind.JavaType) : 228
 com.fasterxml.jackson.databind.deser.DeserializerCache._createAndCacheValueDeserializer(com.fasterxml.jackson.databind.DeserializationContext,com.fasterxml.jackson.databind.deser.DeserializerFactory,com.fasterxml.jackson.databind.JavaType) : 228
 com.fasterxml.jackson.databind.deser.DeserializerCache.findValueDeserializer(com.fasterxml.jackson.databind.DeserializationContext,com.fasterxml.jackson.databind.deser.DeserializerFactory,com.fasterxml.jackson.databind.JavaType) : 228
 com.fasterxml.jackson.databind.DeserializationContext.findRootValueDeserializer(com.fasterxml.jackson.databind.JavaType) : 228
 com.fasterxml.jackson.databind.ObjectReader._prefetchRootDeserializer(com.fasterxml.jackson.databind.JavaType) : 229`
 
1. How to know which classes to include?\
 I am using debugger to understand which classes needs to be instrumented.
 
1. TODO
   1. Make minimal duration configurable;
   1. Allow class matching by prefix, e.g. com.fasterxml.*;
  