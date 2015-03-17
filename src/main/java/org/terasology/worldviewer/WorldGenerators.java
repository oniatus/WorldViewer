/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.worldviewer;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.SimpleUri;
import org.terasology.world.generator.RegisterWorldGenerator;
import org.terasology.world.generator.WorldGenerator;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * Some helper methods for WorldGenerators
 * @author Martin Steiger
 */
public final class WorldGenerators {

    private static final Logger logger = LoggerFactory.getLogger(WorldGenerators.class);

    private WorldGenerators() {
        // no instances
    }

    /**
     * @return a list of world generators on the classpath
     */
    public static Set<Class<?>> findOnClasspath(String packageFilter) {

        // search only in packages with that start with o.t.
        Collection<URL> classPathURLs = ClasspathHelper.forPackage(packageFilter);

        // this effectively removes assets, MANIFEST.MF and other files
        Predicate<String> fileFilter = new FilterBuilder().includePackage(packageFilter);

        Reflections reflections = new Reflections(new ConfigurationBuilder()
             .setUrls(classPathURLs)
             .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner())
             .filterInputsBy(fileFilter));

        // find classes that are annotated and assume that they are WorldGenerator instances
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(RegisterWorldGenerator.class);

        return classes;
    }

    /**
     * @param className the fully qualified class name
     * @return the world generator or <code>null</code>
     */
    public static WorldGenerator createWorldGenerator(String className) {

        try {
            Class<?> worldGenClazz = Class.forName(className);
            if (!WorldGenerator.class.isAssignableFrom(worldGenClazz)) {
                throw new IllegalArgumentException(className + " does not implement the WorldGenerator interface");
            }
            RegisterWorldGenerator anno = worldGenClazz.getAnnotation(RegisterWorldGenerator.class);
            if (anno == null) {
                throw new IllegalArgumentException(className + " is not annotated with @RegisterWorldGenerator");
            }
            Constructor<?> constructor = worldGenClazz.getConstructor(SimpleUri.class);
            return (WorldGenerator) constructor.newInstance(new SimpleUri("unknown", anno.id()));
        } catch (ClassNotFoundException e) {
            logger.info("Class not found: {}", className);
        } catch (LinkageError e) {
            logger.warn("Class not loadable: {}", className, e);
        } catch (NoSuchMethodException e) {
            logger.warn("Class does not have a constructor with SimpleUri parameter", className);
        } catch (SecurityException e) {
            logger.warn("Security violation while loading class {}", className, e);
        } catch (Exception e) {
            logger.warn("Could not instantiate class {}", className);
        }

        return null;
    }

    /**
     * @param clazz the world generator class (possibly annotated with @RegisterWorldGenerator)
     * @return the human-readable name
     */
    public static String getAnnotatedDisplayName(Class<?> clazz) {
        RegisterWorldGenerator anno = clazz.getAnnotation(RegisterWorldGenerator.class);
        if (anno != null) {
            return anno.displayName();
        } else {
            return clazz.getSimpleName();
        }
    }
}
