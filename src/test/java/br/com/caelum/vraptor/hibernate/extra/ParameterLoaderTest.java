package br.com.caelum.vraptor.hibernate.extra;

import br.com.caelum.vraptor.Result;
import br.com.caelum.vraptor.controller.ControllerMethod;
import br.com.caelum.vraptor.converter.*;
import br.com.caelum.vraptor.core.*;
import br.com.caelum.vraptor.http.*;
import br.com.caelum.vraptor.interceptor.SimpleInterceptorStack;
import br.com.caelum.vraptor.view.FlashScope;
import org.hibernate.*;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.Type;
import org.junit.*;
import org.mockito.*;
import org.mockito.stubbing.Stubber;

import javax.persistence.Id;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.*;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class ParameterLoaderTest {

    private @Mock SessionFactory sessionFactory;
    private @Mock ClassMetadata classMetadata;
    private @Mock Type type;
    private @Mock Session session;
    private @Mock HttpServletRequest request;
    private @Mock ParameterNameProvider provider;
    private @Mock Result result;
    private @Mock Converters converters;
    private @Mock FlashScope flash;

    private ParameterLoaderInterceptor parameterLoader;
    private @Mock InterceptorStack stack;
    private @Mock Object instance;
    private @Mock ControllerMethod method;
    private SimpleInterceptorStack simpleInterceptorStack = mock(SimpleInterceptorStack.class);


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        parameterLoader = new ParameterLoaderInterceptor(session, request, provider, result, converters, flash);
        when(method.getMethod()).thenReturn(getMethod("method", Entity.class));
        when(converters.to(Long.class)).thenReturn(new LongConverter());
        when(converters.to(String.class)).thenReturn(new StringConverter());
    }
    
    @Test
    public void shouldLoadEntityUsingId() throws Exception {
    	Parameter[] mocks = mockParameters(Arrays.asList("entity"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);
		
        when(request.getParameter("entity.id")).thenReturn("123");
        Entity expectedEntity = new Entity();
        when(session.get(Entity.class, 123L)).thenReturn(expectedEntity);
        
        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn("id");
        when(classMetadata.getIdentifierType()).thenReturn(type);
        when(type.getReturnedClass()).thenReturn(Long.class);

        parameterLoader.intercept(simpleInterceptorStack, method);

        verify(request).setAttribute("entity", expectedEntity);
    }

    @Test
    public void shouldLoadEntityUsingOtherIdName() throws Exception {
    	when(method.getMethod()).thenReturn(getMethod("methodOtherIdName", EntityOtherIdName.class));
    	Parameter[] mocks = mockParameters(Arrays.asList("entity"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);

        when(request.getParameter("entity.otherIdName")).thenReturn("456");
        EntityOtherIdName expectedEntity = new EntityOtherIdName();
        when(session.get(EntityOtherIdName.class, 456L)).thenReturn(expectedEntity);

        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn("otherIdName");
        when(classMetadata.getIdentifierType()).thenReturn(type);
        when(type.getReturnedClass()).thenReturn(Long.class);

        parameterLoader.intercept(simpleInterceptorStack, method);
        verify(request).setAttribute("entity", expectedEntity);
    }

    private Parameter[] mockParameters(List<String> names) {
    	Parameter[] parameters = new Parameter[names.size()];
    	for (int i = 0; i < parameters.length; i++) {
			Parameter mocked = mock(Parameter.class);
			Mockito.when(mocked.getName()).thenReturn(names.get(i));
			parameters[i] = mocked;
		}
		return parameters;
	}

	@Test
    public void shouldLoadEntityUsingIdOfAnyType() throws Exception {

    	when(method.getMethod()).thenReturn(getMethod("other", OtherEntity.class, String.class));
    	Parameter[] mocks = mockParameters(Arrays.asList("entity", "ignored"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);

        when(request.getParameter("entity.id")).thenReturn("123");
        when(request.getParameter("ignored")).thenReturn("foo bar");
        OtherEntity expectedEntity = new OtherEntity();
        when(session.get(OtherEntity.class, "123")).thenReturn(expectedEntity);

        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn("id");
        when(classMetadata.getIdentifierType()).thenReturn(type);
        when(type.getReturnedClass()).thenReturn(String.class);

        parameterLoader.intercept(simpleInterceptorStack, method);
        verify(request).setAttribute("entity", expectedEntity);
    }

    @Test
    public void shouldOverrideFlashScopedArgsIfAny() throws Exception {
    	Parameter[] mocks = mockParameters(Arrays.asList("entity"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);
        when(request.getParameter("entity.id")).thenReturn("123");
        Object[] args = {new Entity()};

        when(flash.consumeParameters(method)).thenReturn(args);

        Entity expectedEntity = new Entity();
        when(session.get(Entity.class, 123l)).thenReturn(expectedEntity);

        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn("id");
        when(classMetadata.getIdentifierType()).thenReturn(type);
        when(type.getReturnedClass()).thenReturn(Long.class);

        parameterLoader.intercept(simpleInterceptorStack, method);
        assertThat(args[0], is((Object) expectedEntity));

        verify(flash).includeParameters(method, args);
    }

    @Test
    public void shouldSend404WhenNoIdIsSet() throws Exception {
    	Parameter[] mocks = mockParameters(Arrays.asList("entity"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);
        when(request.getParameter("entity.id")).thenReturn(null);

        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn("id");
        when(classMetadata.getIdentifierType()).thenReturn(type);
        when(type.getReturnedClass()).thenReturn(Long.class);

        parameterLoader.intercept(simpleInterceptorStack, method);
        verify(request, never()).setAttribute(eq("entity"), any());
        verify(result).notFound();
    }

    @Test
    public void shouldSend404WhenIdDoesntExist() throws Exception {
    	Parameter[] mocks = mockParameters(Arrays.asList("entity"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);
        when(request.getParameter("entity.id")).thenReturn("123");
        when(session.get(Entity.class, 123l)).thenReturn(null);

        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn("id");
        when(classMetadata.getIdentifierType()).thenReturn(type);
        when(type.getReturnedClass()).thenReturn(Long.class);

        parameterLoader.intercept(simpleInterceptorStack, method);
        verify(request, never()).setAttribute(eq("entity"), any());
        verify(result).notFound();
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentIfEntityDoesntHaveId() throws Exception {
    	when(method.getMethod()).thenReturn(getMethod("noId", NoIdEntity.class));
    	Parameter[] mocks = mockParameters(Arrays.asList("entity"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);
        when(request.getParameter("entity.id")).thenReturn("123");

        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn(null);

        fail().when(request).setAttribute(eq("entity"), any());
        fail().when(result).notFound();

        parameterLoader.intercept(simpleInterceptorStack, method);
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldThrowIllegalArgumentIfIdIsNotConvertable() throws Exception {
    	Parameter[] mocks = mockParameters(Arrays.asList("entity"));
		when(provider.parametersFor(method.getMethod())).thenReturn(mocks);
        when(request.getParameter("entity.id")).thenReturn("123");
        when(converters.to(Long.class)).thenReturn(null);
        fail().when(request).setAttribute(eq("entity"), any());
        fail().when(result).notFound();
        fail().when(stack).next(method, instance);

        when(session.getSessionFactory()).thenReturn(sessionFactory);
        when(sessionFactory.getClassMetadata(any(Class.class))).thenReturn(classMetadata);
        when(classMetadata.getIdentifierPropertyName()).thenReturn("id");
        when(classMetadata.getIdentifierType()).thenReturn(type);
        when(type.getReturnedClass()).thenReturn(Long.class);

        parameterLoader.intercept(simpleInterceptorStack, method);
    }


    static class Entity {
        @Id Long id;
    }
    static class OtherEntity {
        @Id String id;
    }
    static class NoIdEntity {
    }
    
    static class EntityOtherIdName {
        @Id Long otherIdName;
    }
    
    static class Resource {
        public void method(@Load Entity entity) {
        }
        public void other(@Load OtherEntity entity, String ignored) {
        }
        public void noId(@Load NoIdEntity entity) {
        }
        public void methodOtherIdName(@Load EntityOtherIdName entity) {
        }
        public void methodWithoutLoad() {
        }
    }
    
    private Stubber fail() {
        return doThrow(new AssertionError());
    }
    
	private Method getMethod(String methodName, Class<?>...classes){
		try {
			return Resource.class.getMethod(methodName, classes);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
