/*
 * =============================================================================
 * 
 *   Copyright (c) 2011-2016, The THYMELEAF team (http://www.thymeleaf.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package thymeleafsandbox.springreactive.thymeleaf;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.context.AbstractEngineContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.EngineContext;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.ILazyContextVariable;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.inline.IInliner;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.util.Validate;
import reactor.core.publisher.Mono;

/**
 *
 * @author Daniel Fern&aacute;ndez
 *
 * @since 3.0.0
 *
 */
public class SpringReactiveWebEngineContext
        extends AbstractEngineContext implements IEngineContext, ISpringReactiveWebEngineContext {

    /*
     * -------------------------------------------------------------------------------
     * THIS MAP FORWARDS ALL OPERATIONS TO THE UNDERLYING EXCHANGE ATTRIBUTES, EXCEPT
     * FOR THE param (request parameters) AND session (session attributes) VARIABLES.
     *
     * NOTE that, even if attributes are leveled so that above level 0 they are
     * considered local and thus disappear after lowering the level, attributes
     * directly set on the exchange attributes map are considered global and therefore
     * valid even when the level decreased (though they can be overridden). This
     * is so for better simulating the effect of directly working against the
     * exchange object, and for better integration with other template
     * engines or view-layer technologies that expect the ServerWebExchange object to
     * be the 'only source of truth' for context variables.
     * -------------------------------------------------------------------------------
     */

    private static final String PARAM_VARIABLE_NAME = "param";
    private static final String SESSION_VARIABLE_NAME = "session";

    private final ServerHttpRequest request;
    private final ServerHttpResponse response;
    private final Mono<WebSession> session;
    private final ServerWebExchange exchange;

    private final WebExchangeAttributesVariablesMap webExchangeAttributesVariablesMap;
    private final Map<String,Object> requestParametersVariablesMap;
    private final Map<String,Object> sessionAttributesVariablesMap;




    /**
     * <p>
     *   Creates a new instance of this {@link IEngineContext} implementation binding engine execution to
     *   the Spring Reactive request handling mechanisms, mainly modelled by {@link ServerWebExchange}.
     * </p>
     * <p>
     *   Note that implementations of {@link IEngineContext} are not meant to be used in order to call
     *   the template engine (use implementations of {@link IContext} such as {@link Context} or {@link WebContext}
     *   instead). This is therefore mostly an <b>internal</b> implementation, and users should have no reason
     *   to ever call this constructor except in very specific integration/extension scenarios.
     * </p>
     *
     * @param configuration the configuration instance being used.
     * @param templateData the template data for the template to be processed.
     * @param templateResolutionAttributes the template resolution attributes.
     * @param exchange the web exchange object being used for request handling.
     * @param locale the locale.
     * @param variables the context variables, probably coming from another {@link IContext} implementation.
     */
    public SpringReactiveWebEngineContext(
            final IEngineConfiguration configuration,
            final TemplateData templateData,
            final Map<String,Object> templateResolutionAttributes,
            final ServerWebExchange exchange,
            final Locale locale,
            final Map<String, Object> variables) {

        super(configuration, templateResolutionAttributes, locale);

        Validate.notNull(exchange, "Server Web Exchange cannot be null in web variables map");

        this.exchange = exchange;
        this.request = this.exchange.getRequest();
        this.response = this.exchange.getResponse();
        this.session = this.exchange.getSession();

        this.webExchangeAttributesVariablesMap =
                new WebExchangeAttributesVariablesMap(configuration, templateData, templateResolutionAttributes, this.exchange, locale, variables);
        this.requestParametersVariablesMap = new RequestParametersMap(this.request);
        this.sessionAttributesVariablesMap = new SessionAttributesMap(this.session);

    }


    public ServerHttpRequest getRequest() {
        return this.request;
    }


    public ServerHttpResponse getResponse() {
        return this.response;
    }


    public Mono<WebSession> getSession() {
        return this.session;
    }


    public ServerWebExchange getExchange() {
        return this.exchange;
    }


    public boolean containsVariable(final String name) {
        return SESSION_VARIABLE_NAME.equals(name) ||
                PARAM_VARIABLE_NAME.equals(name) ||
                this.webExchangeAttributesVariablesMap.containsVariable(name);
    }


    public Object getVariable(final String key) {
        if (SESSION_VARIABLE_NAME.equals(key)) {
            return this.sessionAttributesVariablesMap;
        }
        if (PARAM_VARIABLE_NAME.equals(key)) {
            return this.requestParametersVariablesMap;
        }
        return this.webExchangeAttributesVariablesMap.getVariable(key);
    }


    public Set<String> getVariableNames() {
        // Note this set will NOT include 'param', 'session' or 'application', as they are considered special
        // ways to access attributes/parameters in these Servlet API structures
        return this.webExchangeAttributesVariablesMap.getVariableNames();
    }


    public void setVariable(final String name, final Object value) {
        if (SESSION_VARIABLE_NAME.equals(name) || PARAM_VARIABLE_NAME.equals(name)) {
            throw new IllegalArgumentException(
                    "Cannot set variable called '" + name + "' into web variables map: such name is a reserved word");
        }
        this.webExchangeAttributesVariablesMap.setVariable(name, value);
    }


    public void setVariables(final Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        // First perform reserved word check on every variable name to be inserted
        for (final String name : variables.keySet()) {
            if (SESSION_VARIABLE_NAME.equals(name) || PARAM_VARIABLE_NAME.equals(name)) {
                throw new IllegalArgumentException(
                        "Cannot set variable called '" + name + "' into web variables map: such name is a reserved word");
            }
        }
        this.webExchangeAttributesVariablesMap.setVariables(variables);
    }


    public void removeVariable(final String name) {
        if (SESSION_VARIABLE_NAME.equals(name) || PARAM_VARIABLE_NAME.equals(name)) {
            throw new IllegalArgumentException(
                    "Cannot remove variable called '" + name + "' in web variables map: such name is a reserved word");
        }
        this.webExchangeAttributesVariablesMap.removeVariable(name);
    }


    public boolean isVariableLocal(final String name) {
        return this.webExchangeAttributesVariablesMap.isVariableLocal(name);
    }


    public boolean hasSelectionTarget() {
        return this.webExchangeAttributesVariablesMap.hasSelectionTarget();
    }


    public Object getSelectionTarget() {
        return this.webExchangeAttributesVariablesMap.getSelectionTarget();
    }


    public void setSelectionTarget(final Object selectionTarget) {
        this.webExchangeAttributesVariablesMap.setSelectionTarget(selectionTarget);
    }




    public IInliner getInliner() {
        return this.webExchangeAttributesVariablesMap.getInliner();
    }

    public void setInliner(final IInliner inliner) {
        this.webExchangeAttributesVariablesMap.setInliner(inliner);
    }




    public TemplateData getTemplateData() {
        return this.webExchangeAttributesVariablesMap.getTemplateData();
    }

    public void setTemplateData(final TemplateData templateData) {
        this.webExchangeAttributesVariablesMap.setTemplateData(templateData);
    }


    public List<TemplateData> getTemplateStack() {
        return this.webExchangeAttributesVariablesMap.getTemplateStack();
    }




    public void setElementTag(final IProcessableElementTag elementTag) {
        this.webExchangeAttributesVariablesMap.setElementTag(elementTag);
    }




    public List<IProcessableElementTag> getElementStack() {
        return this.webExchangeAttributesVariablesMap.getElementStack();
    }


    public List<IProcessableElementTag> getElementStackAbove(final int contextLevel) {
        return this.webExchangeAttributesVariablesMap.getElementStackAbove(contextLevel);
    }




    public int level() {
        return this.webExchangeAttributesVariablesMap.level();
    }


    public void increaseLevel() {
        this.webExchangeAttributesVariablesMap.increaseLevel();
    }


    public void decreaseLevel() {
        this.webExchangeAttributesVariablesMap.decreaseLevel();
    }




    public String getStringRepresentationByLevel() {
        // Request parameters, session and servlet context can be safely ignored here
        return this.webExchangeAttributesVariablesMap.getStringRepresentationByLevel();
    }




    @Override
    public String toString() {
        // Request parameters, session and servlet context can be safely ignored here
        return this.webExchangeAttributesVariablesMap.toString();
    }



    static Object resolveLazy(final Object variable) {
        /*
         * Check the possibility that this variable is a lazy one, in which case we should not return it directly
         * but instead make sure it is initialized and return its value.
         */
        if (variable != null && variable instanceof ILazyContextVariable) {
            return ((ILazyContextVariable)variable).getValue();
        }
        return variable;
    }




    private static final class SessionAttributesMap extends NoOpMapImpl {

        /*
         * Note this class is built so that the Mono flux containing the WebSession is not really blocked
         * (and the real WebSession object obtained) until really needed.
         */

        private final Mono<WebSession> sessionFlux;
        private WebSession session = null;


        SessionAttributesMap(final Mono<WebSession> sessionFlux) {
            super();
            this.sessionFlux = sessionFlux;
        }

        private WebSession getSession() {
            if (this.session == null) {
                this.session = this.sessionFlux.block();
            }
            return this.session;
        }

        @Override
        public int size() {
            return getSession().getAttributes().size();
        }

        @Override
        public boolean isEmpty() {
            return getSession().getAttributes().isEmpty();
        }

        @Override
        public boolean containsKey(final Object key) {
            // Even if not completely correct to return 'true' for entries that might not exist, this is needed
            // in order to avoid Spring's MapAccessor throwing an exception when trying to access an element
            // that doesn't exist -- in the case of request parameters, session and servletContext attributes most
            // developers would expect null to be returned in such case, and that's what this 'true' will cause.
            return true;
        }

        @Override
        public boolean containsValue(final Object value) {
            // It wouldn't be consistent to have an 'ad hoc' implementation of #containsKey() but a 100% correct
            // implementation of #containsValue(), so we are leaving this as unsupported.
            throw new UnsupportedOperationException("Map does not support #containsValue()");
        }

        @Override
        public Object get(final Object key) {
            return resolveLazy(getSession().getAttributes().get(key != null? key.toString() : null));
        }

        @Override
        public Set<String> keySet() {
            return getSession().getAttributes().keySet();
        }

        @Override
        public Collection<Object> values() {
            return getSession().getAttributes().values();
        }

        @Override
        public Set<Entry<String,Object>> entrySet() {
            return getSession().getAttributes().entrySet();
        }

    }




    private static final class RequestParametersMap extends NoOpMapImpl {

        private final ServerHttpRequest request;

        RequestParametersMap(final ServerHttpRequest request) {
            super();
            this.request = request;
        }


        @Override
        public int size() {
            return this.request.getQueryParams().size();
        }

        @Override
        public boolean isEmpty() {
            return this.request.getQueryParams().isEmpty();
        }

        @Override
        public boolean containsKey(final Object key) {
            // Even if not completely correct to return 'true' for entries that might not exist, this is needed
            // in order to avoid Spring's MapAccessor throwing an exception when trying to access an element
            // that doesn't exist -- in the case of request parameters, session and servletContext attributes most
            // developers would expect null to be returned in such case, and that's what this 'true' will cause.
            return true;
        }

        @Override
        public boolean containsValue(final Object value) {
            // It wouldn't be consistent to have an 'ad hoc' implementation of #containsKey() but a 100% correct
            // implementation of #containsValue(), so we are leaving this as unsupported.
            throw new UnsupportedOperationException("Map does not support #containsValue()");
        }

        @Override
        public Object get(final Object key) {
            final List<String> parameterValues = this.request.getQueryParams().get(key != null? key.toString() : null);
            if (parameterValues == null) {
                return null;
            }
            return new RequestParameterValues(parameterValues);
        }

        @Override
        public Set<String> keySet() {
            return this.request.getQueryParams().keySet();
        }

        @Override
        public Collection<Object> values() {
            return (Collection<Object>) (Collection<?>) this.request.getQueryParams().values();
        }

        @Override
        public Set<Entry<String,Object>> entrySet() {
            return (Set<Entry<String,Object>>) (Set<?>) this.request.getQueryParams().entrySet();
        }

    }




    private static final class WebExchangeAttributesVariablesMap extends EngineContext {

        private final ServerWebExchange exchange;


        WebExchangeAttributesVariablesMap(
                final IEngineConfiguration configuration,
                final TemplateData templateData,
                final Map<String,Object> templateResolutionAttributes,
                final ServerWebExchange exchange,
                final Locale locale,
                final Map<String, Object> variables) {

            super(configuration, templateData, templateResolutionAttributes, locale, variables);
            this.exchange = exchange;

        }


        public boolean containsVariable(final String name) {
            if (super.containsVariable(name)) {
                return true;
            }
            return this.exchange.getAttributes().containsKey(name);
        }


        public Object getVariable(final String key) {
            final Object value = super.getVariable(key);
            if (value != null) {
                return value;
            }
            return this.exchange.getAttributes().get(key);
        }


        public Set<String> getVariableNames() {
            final Set<String> variableNames = super.getVariableNames();
            variableNames.addAll(this.exchange.getAttributes().keySet());
            return variableNames;
        }


        public String getStringRepresentationByLevel() {
            final StringBuilder strBuilder = new StringBuilder(super.getStringRepresentationByLevel());
            strBuilder.append("[[EXCHANGE: " + this.exchange.getAttributes() + "]]");
            return strBuilder.toString();
        }




        @Override
        public String toString() {
            final StringBuilder strBuilder = new StringBuilder(super.toString());
            strBuilder.append("[[EXCHANGE: " + this.exchange.getAttributes() + "]]");
            return strBuilder.toString();
        }

    }





    private abstract static class NoOpMapImpl implements Map<String,Object> {

        protected NoOpMapImpl() {
            super();
        }

        public int size() {
            return 0;
        }

        public boolean isEmpty() {
            return true;
        }

        public boolean containsKey(final Object key) {
            return false;
        }

        public boolean containsValue(final Object value) {
            return false;
        }

        public Object get(final Object key) {
            return null;
        }

        public Object put(final String key, final Object value) {
            throw new UnsupportedOperationException("Cannot add new entry: map is immutable");
        }

        public Object remove(final Object key) {
            throw new UnsupportedOperationException("Cannot remove entry: map is immutable");
        }

        public void putAll(final Map<? extends String, ? extends Object> m) {
            throw new UnsupportedOperationException("Cannot add new entry: map is immutable");
        }

        public void clear() {
            throw new UnsupportedOperationException("Cannot clear: map is immutable");
        }

        public Set<String> keySet() {
            return Collections.emptySet();
        }

        public Collection<Object> values() {
            return Collections.emptyList();
        }

        public Set<Entry<String,Object>> entrySet() {
            return Collections.emptySet();
        }


        static final class MapEntry implements Entry<String,Object> {

            private final String key;
            private final Object value;

            MapEntry(final String key, final Object value) {
                super();
                this.key = key;
                this.value = value;
            }

            public String getKey() {
                return this.key;
            }

            public Object getValue() {
                return this.value;
            }

            public Object setValue(final Object value) {
                throw new UnsupportedOperationException("Cannot set value: map is immutable");
            }

        }


    }



    private static final class RequestParameterValues extends AbstractList<String> {

        private final List<String> parameterValues;

        RequestParameterValues(final List<String> parameterValues) {
            this.parameterValues = parameterValues;
        }

        @Override
        public int size() {
            return this.parameterValues.size();
        }

        @Override
        public Object[] toArray() {
            return this.parameterValues.toArray();
        }

        @Override
        public <T> T[] toArray(final T[] arr) {
            return this.parameterValues.toArray(arr);
        }

        @Override
        public String get(final int index) {
            return this.parameterValues.get(index);
        }

        @Override
        public int indexOf(final Object obj) {
            return this.parameterValues.indexOf(obj);
        }

        @Override
        public boolean contains(final Object obj) {
            return this.parameterValues.contains(obj);
        }


        @Override
        public String toString() {
            // This toString() method will be responsible of outputting non-indexed request parameters in the
            // way most people expect, i.e. return parameterValues[0] when accessed without index and parameter is
            // single-valued (${param.a}), returning ArrayList#toString() when accessed without index and parameter
            // is multi-valued, and finally return the specific value when accessed with index (${param.a[0]})
            final int size = this.parameterValues.size();
            if (size == 0) {
                return "";
            }
            if (size == 1) {
                return this.parameterValues.get(0);
            }
            return this.parameterValues.toString();
        }
    }

}