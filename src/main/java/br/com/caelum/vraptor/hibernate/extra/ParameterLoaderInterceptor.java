/***
 * Copyright (c) 2009 Caelum - www.caelum.com.br/opensource All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package br.com.caelum.vraptor.hibernate.extra;


import br.com.caelum.vraptor.*;
import br.com.caelum.vraptor.controller.ControllerMethod;
import br.com.caelum.vraptor.converter.Converter;
import br.com.caelum.vraptor.core.*;
import br.com.caelum.vraptor.http.*;
import br.com.caelum.vraptor.interceptor.Interceptor;
import br.com.caelum.vraptor.view.FlashScope;
import com.google.common.collect.Iterables;
import org.hibernate.Session;
import org.hibernate.type.Type;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.isEmpty;
import static java.util.Arrays.asList;

/**
 * Interceptor that loads given entity from the database.
 *
 * @author Lucas Cavalcanti
 * @author Cecilia Fernandes
 * @author Otávio Scherer Garcia
 * @since 3.4.0
 *
 */
@Intercepts
public class ParameterLoaderInterceptor implements Interceptor {

	private final Session session;
	private final HttpServletRequest request;
	private final ParameterNameProvider provider;
	private final Result result;
	private final Converters converters;
	private final FlashScope flash;


	/**
	 * @deprecated CDI eyes only
	 */
	protected ParameterLoaderInterceptor(){
		this(null,null,null,null,null,null);
	}


	@Inject
	public ParameterLoaderInterceptor(Session session, HttpServletRequest request, ParameterNameProvider provider,
									  Result result, Converters converters, FlashScope flash) {
		this.session = session;
		this.request = request;
		this.provider = provider;
		this.result = result;
		this.converters = converters;
		this.flash = flash;
	}

	public boolean accepts(ControllerMethod method) {
		return method.containsAnnotation(Load.class);
	}

	public void intercept(InterceptorStack stack, ControllerMethod method, Object resourceInstance)
			throws InterceptionException {
		Annotation[][] annotations = method.getMethod().getParameterAnnotations();
		final Parameter[] parameters = provider.parametersFor(method.getMethod());
		final Class<?>[] types = method.getMethod().getParameterTypes();
		final Object[] args = flash.consumeParameters(method);

		for (int i = 0; i < parameters.length; i++) {
			if (hasLoadAnnotation(annotations[i])) {
				Parameter parameter = parameters[i];
				Object loaded = load(parameter.getName(), types[i]);

				// TODO extract to method, so users can override behaviour
				if (loaded == null) {
					result.notFound();
					return;
				}

				if (args != null) {
					args[i] = loaded;
				} else {
					request.setAttribute(parameter.getName(), loaded);
				}
			}
		}

		flash.includeParameters(method, args);
		stack.next(method, resourceInstance);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Object load(String name, Class type) {
		String idProperty = session.getSessionFactory().getClassMetadata(type).getIdentifierPropertyName();
		checkArgument(idProperty != null, "Entity %s must have an id property for @Load.", type.getSimpleName());

		String parameter = request.getParameter(name + "." + idProperty);
		if (parameter == null) {
			return null;
		}

		Type idType = session.getSessionFactory().getClassMetadata(type).getIdentifierType();
		Converter<?> converter = converters.to(idType.getReturnedClass());
		checkArgument(converter != null, "Entity %s id type %s must have a converter",
				type.getSimpleName(), idType);

		Serializable id = (Serializable) converter.convert(parameter, type);
		return session.get(type, id);
	}

	private boolean hasLoadAnnotation(Annotation[] annotations) {
		return !isEmpty(Iterables.filter(asList(annotations), Load.class));
	}
}