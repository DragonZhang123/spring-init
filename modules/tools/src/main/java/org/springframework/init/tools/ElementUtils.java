/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.init.tools;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.init.func.ConditionService;
import org.springframework.init.func.DefaultTypeService;
import org.springframework.init.func.FunctionalInstallerListener;
import org.springframework.init.func.InfrastructureUtils;
import org.springframework.init.func.TypeService;
import org.springframework.util.ClassUtils;

import com.squareup.javapoet.ClassName;

/**
 * @author Dave Syer
 *
 */
public class ElementUtils {

	private static Log logger = LogFactory.getLog(ElementUtils.class);

	private GenericApplicationContext main;

	public boolean hasAnnotation(AnnotatedElement element, String type) {
		return getAnnotation(element, type) != null;
	}

	public Set<Annotation> getAnnotations(AnnotatedElement element, String type) {
		Set<Annotation> set = new HashSet<>();
		getAnnotations(element, type, set, new HashSet<>());
		return set;
	}

	private void getAnnotations(AnnotatedElement element, String type, Set<Annotation> set, Set<Annotation> seen) {

		if (element != null) {
			for (Annotation annotation : element.getAnnotations()) {
				String annotationTypename = annotation.annotationType().getName();
				try {
					if (annotationTypename.startsWith("java.lang")) {
						continue;
					}
					if (annotationTypename.equals(SpringClassNames.NULLABLE.toString())) {
						continue;
					}
					if (type.equals(annotationTypename)) {
						set.add(annotation);
						continue;
					}
					if (!seen.contains(annotation)) {
						seen.add(annotation);
						getAnnotations(annotation.annotationType(), type, set, seen);
					}
				}
				catch (Throwable t) {
					logger.warn("Problems working with annotation " + annotationTypename);
				}
			}
		}

	}

	public Annotation getAnnotation(AnnotatedElement element, String type) {
		return getAnnotation(element, type, new HashSet<>());
	}

	private Annotation getAnnotation(AnnotatedElement element, String type, Set<Annotation> seen) {
		if (element != null) {
			try {
				for (Annotation annotation : element.getAnnotations()) {
					String annotationTypename = annotation.annotationType().getName();
					try {
						if (annotationTypename.startsWith("java.lang")) {
							continue;
						}
						if (annotationTypename.equals(SpringClassNames.NULLABLE.toString())) {
							continue;
						}
						if (type.equals(annotationTypename)) {
							return annotation;
						}
						if (!seen.contains(annotation)) {
							seen.add(annotation);
							annotation = getAnnotation(annotation.annotationType(), type, seen);
							if (annotation != null) {
								return annotation;
							}
						}
					}
					catch (Throwable t) {
						logger.error("Problems working with annotation " + annotationTypename, t);
					}
				}
			}
			catch (ArrayStoreException e) {
				// ignore
			}
		}
		return null;
	}

	static Set<String> NO_METADATA_IMPORTS = new HashSet<>(
			Arrays.asList(SpringClassNames.REACTIVE_BEAN_POST_PROCESSORS.reflectionName(),
					SpringClassNames.SERVLET_BEAN_POST_PROCESSORS.reflectionName()));

	public boolean isImportWithNoMetadata(Class<?> imported) {
		// TODO: There are some imported registrars that don't use the annotation
		// metadata. We can enumerate them all eventually.
		return NO_METADATA_IMPORTS.contains(imported.getName().replace("$", "."));
	}

	public boolean isImporter(Class<?> imported) {
		return isImportSelector(imported) || isImportBeanDefinitionRegistrar(imported);
	}

	public boolean isImportSelector(Class<?> imported) {
		return implementsInterface(imported, ImportSelector.class);
	}

	public boolean isDeferredImportSelector(Class<?> imported) {
		return implementsInterface(imported, DeferredImportSelector.class);
	}

	public boolean isImportBeanDefinitionRegistrar(Class<?> imported) {
		return implementsInterface(imported, ImportBeanDefinitionRegistrar.class);
	}

	public boolean isConfigurationProperties(Class<?> imported) {
		return imported.getName().equals(SpringClassNames.ENABLE_CONFIGURATION_PROPERTIES_REGISTRAR.reflectionName());
	}

	public Class<?> getSuperType(Class<?> type) {
		Class<?> superType = type.getSuperclass();
		return (superType == null ? null : superType);
	}

	public Class<?> getReturnType(Method method) {
		Class<?> type = method.getReturnType();
		if (Modifier.isPrivate(type.getModifiers())) {
			// Hack, hack, hackety, hack...
			for (Class<?> element : type.getInterfaces()) {
				// Find an interface, any interface...
				if (Modifier.isPublic(element.getModifiers())) {
					return element;
				}
			}
		}
		return type;
	}

	public String getQualifiedName(Class<?> type) {
		return type.getName();
	}

	public List<Class<?>> getTypesFromAnnotation(Annotation anno, String fieldname) {
		List<Class<?>> collected = new ArrayList<>();
		if (anno != null) {
			Map<String, Object> values = AnnotationUtils.getAnnotationAttributes(anno);
			for (String element : values.keySet()) {
				if (element.equals(fieldname)) {
					Object value = values.get(fieldname);
					if (value instanceof Class<?>) {
						collected.add((Class<?>) value);
					}
					else if (value instanceof Object[]) {
						for (Object val : (Object[]) value) {
							if (val instanceof Class<?>) {
								collected.add((Class<?>) val);
							}
						}
					}
				}
			}
		}
		return collected;
	}

	public List<Annotation> getAnnotationsFromAnnotation(Annotation anno, String fieldname) {
		List<Annotation> collected = new ArrayList<>();
		if (anno != null) {
			Map<String, Object> values = AnnotationUtils.getAnnotationAttributes(anno);
			for (String element : values.keySet()) {
				if (element.equals(fieldname)) {
					Object value = values.get(fieldname);
					if (value instanceof Annotation) {
						collected.add((Annotation) value);
					}
					else if (value instanceof Object[]) {
						for (Object val : (Object[]) value) {
							if (val instanceof Annotation) {
								collected.add((Annotation) val);
							}
						}
					}
				}
			}
		}
		return collected;
	}

	public List<String> getStringsFromAnnotation(Annotation anno, String fieldname) {
		List<String> collected = new ArrayList<>();
		if (anno != null) {
			Map<String, Object> values = AnnotationUtils.getAnnotationAttributes(anno);
			for (String element : values.keySet()) {
				if (element.equals(fieldname)) {
					Object value = values.get(fieldname);
					if (value instanceof String) {
						collected.add((String) value);
					}
					else if (value instanceof Object[]) {
						for (Object val : (Object[]) value) {
							if (val instanceof String) {
								collected.add((String) val);
							}
						}
					}
				}
			}
		}
		return collected;
	}

	public boolean throwsCheckedException(Method beanMethod) {
		for (Class<?> type : beanMethod.getExceptionTypes()) {
			if (Exception.class.isAssignableFrom(type) && !RuntimeException.class.isAssignableFrom(type)) {
				return true;
			}
		}
		return false;
	}

	public String getParameterType(Parameter param) {
		return param.getType().getName();
	}

	public Class<?> asElement(Class<?> type) {
		return type;
	}

	public Class<?> asElement(String typename) {
		return ClassUtils.resolveClassName(typename, null);
	}

	public List<Class<?>> getTypesFromAnnotation(Class<?> type, String annotation, String attribute) {
		Set<Class<?>> list = new LinkedHashSet<>();
		for (Annotation mirror : type.getAnnotations()) {
			if (mirror.annotationType().getName().equals(annotation)) {
				list.addAll(getTypesFromAnnotation(mirror, attribute));
			}
			Set<Annotation> metas = getAnnotations(mirror.annotationType(), annotation);
			for (Annotation meta : metas) {
				list.addAll(getTypesFromAnnotation(meta, attribute));
			}
		}
		return new ArrayList<>(list);
	}

	public List<Annotation> getAnnotationsFromAnnotation(Class<?> type, String annotation, String attribute) {
		Set<Annotation> list = new LinkedHashSet<>();
		for (Annotation mirror : type.getAnnotations()) {
			if (mirror.annotationType().getName().equals(annotation)) {
				list.addAll(getAnnotationsFromAnnotation(mirror, attribute));
			}
			Annotation meta = getAnnotation(mirror.annotationType(), annotation);
			if (meta != null) {
				list.addAll(getAnnotationsFromAnnotation(meta, attribute));
			}
		}
		return new ArrayList<>(list);
	}

	public String getStringFromAnnotation(AnnotatedElement type, String annotation, String attribute) {
		List<String> list = getStringsFromAnnotation(type, annotation, attribute);
		return list.isEmpty() ? null : list.iterator().next();
	}

	public List<String> getStringsFromAnnotation(AnnotatedElement type, String annotation, String attribute) {
		Set<String> list = new HashSet<>();
		for (Annotation mirror : type.getAnnotations()) {
			if (mirror.annotationType().getName().equals(annotation)) {
				list.addAll(getStringsFromAnnotation(mirror, attribute));
			}
			Annotation meta = getAnnotation(mirror.annotationType(), annotation);
			if (meta != null) {
				list.addAll(getStringsFromAnnotation(meta, attribute));
			}
		}
		return new ArrayList<>(list);
	}

	public boolean implementsInterface(Class<?> te, ClassName intface) {
		if (!ClassUtils.isPresent(intface.toString(), null)) {
			return false;
		}
		return implementsInterface(te, ClassUtils.resolveClassName(intface.toString(), null));
	}

	public boolean implementsInterface(Class<?> te, Class<?> intface) {
		if (te == null) {
			return false;
		}
		if (te == intface) {
			return true;
		}
		return intface.isAssignableFrom(te);
	}

	public String getPackage(Class<?> imported) {
		return ClassName.get(imported).packageName();
	}

	public String getQualifier(Parameter param) {
		if (!hasAnnotation(param, SpringClassNames.QUALIFIER.toString())) {
			return null;
		}
		String qualifier = getStringFromAnnotation(param, SpringClassNames.QUALIFIER.toString(), "value");
		return qualifier != null && qualifier.length() == 0 ? null : qualifier;
	}

	public boolean isLazy(Parameter param) {
		return hasAnnotation(param, SpringClassNames.LAZY.toString());
	}

	public String erasure(Type type) {
		if (type instanceof Class) {
			return ((Class<?>) type).getName();
		}
		String name = type.toString();
		return name.contains("<") ? name.substring(0, name.indexOf("<")) : name;
	}

	public Class<?>[] getMemberClasses(Class<?> type) {
		Class<?>[] classes = type.getDeclaredClasses();
		for (int i = 0; i < classes.length; i++) {
			Class<?> cls = classes[i];
			// Hack for DispatcherServletAutoConfiguration
			if (cls.getName().endsWith("RegistrationConfiguration") && i < classes.length - 1) {
				classes[i] = classes[i + 1];
				classes[i + 1] = cls;
				break;
			}
		}
		return classes;
	}

	public boolean isAutoConfigurationPackages(Class<?> imported) {
		return imported.getName().equals(SpringClassNames.AUTOCONFIGURATION_PACKAGES.toString() + "$Registrar");
	}

	private GenericApplicationContext getMain() {
		if (this.main == null) {
			// TODO: Read application.properties?
			GenericApplicationContext context = new GenericApplicationContext();
			this.main = new GenericApplicationContext();
			context.refresh();
			InfrastructureUtils.install(main.getBeanFactory(), context);
			context.getBeanFactory().registerSingleton(TypeService.class.getName(),
					new DefaultTypeService(context.getClassLoader()));
			context.getBeanFactory().registerSingleton(ConditionService.class.getName(),
					new OnClassConditionService(main));
			FunctionalInstallerListener.initialize(main);
		}
		return this.main;
	}

	public ImportSelector getImportSelector(Class<?> imported) {
		ImportSelector selector = (ImportSelector) InfrastructureUtils.getOrCreate(getMain(), imported);
		if (selector instanceof ResourceLoaderAware) {
			((ResourceLoaderAware) selector).setResourceLoader(new DefaultResourceLoader());
		}
		return selector;
	}

	public boolean isIncluded(Class<?> imported) {
		ConditionService conditions = InfrastructureUtils.getBean(getMain().getBeanFactory(), ConditionService.class);
		return conditions.matches(imported, ConfigurationPhase.PARSE_CONFIGURATION);
	}

}
