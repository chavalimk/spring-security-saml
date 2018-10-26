/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package sample.proof_of_concept;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.saml.SamlTemplateEngine;
import org.springframework.security.saml.registration.ExternalProviderConfiguration;
import org.springframework.security.saml.registration.HostedServiceProviderConfiguration;
import org.springframework.security.saml.saml2.metadata.IdentityProviderMetadata;
import org.springframework.security.saml.saved_for_later.HostedServiceProvider;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

public class SelectIdentityProviderUIFilter extends SamlFilter {

	private static Log logger = LogFactory.getLog(SelectIdentityProviderUIFilter.class);

	private final StaticServiceProviderResolver resolver;
	private RequestMatcher requestMatcher = new AntPathRequestMatcher("/saml/sp/select/**");;
	private String selectTemplate = "/templates/spi/select-provider.vm";
	private boolean redirectOnSingleProvider = true;

	protected SelectIdentityProviderUIFilter(SamlTemplateEngine samlTemplateEngine,
											 StaticServiceProviderResolver resolver) {
		super(samlTemplateEngine);
		this.resolver = resolver;
	}


	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {
		if (requestMatcher.matches(request)) {
			HostedServiceProvider provider = resolver.resolve(request);
			HostedServiceProviderConfiguration configuration = provider.getConfiguration();
			List<ModelProvider> providers = new LinkedList<>();
			configuration.getProviders().stream().forEach(
				p -> {
					try {
						ModelProvider mp = new ModelProvider()
							.setLinkText(p.getLinktext())
							.setRedirect(getDiscoveryRedirect(provider, p));
						providers.add(mp);
					} catch (Exception x) {
						logger.debug(format(
							"Unable to retrieve metadata for provider:%s with message:",
							p.getMetadata(),
							x.getMessage()
							)
						);
					}
				}
			);
			if (providers.size() == 1 && isRedirectOnSingleProvider()) {
				response.sendRedirect(providers.get(0).getRedirect());
			}
			else {
				Map<String, Object> model = new HashMap<>();
				model.put("title", "Select an Identity Provider");
				model.put("providers", providers);
				processHtmlBody(
					request,
					response,
					selectTemplate,
					model
				);
			}
		}
		else {
			filterChain.doFilter(request, response);
		}
	}

	private String getDiscoveryRedirect(HostedServiceProvider provider,
										  ExternalProviderConfiguration p) throws UnsupportedEncodingException {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
			provider.getConfiguration().getBasePath()
		);
		builder.pathSegment("saml/sp/discovery");
		IdentityProviderMetadata metadata = provider.getRemoteProviders().get(p);
		builder.queryParam("idp", UriUtils.encode(metadata.getEntityId(), UTF_8.toString()));
		return builder.build().toUriString();
	}

	public RequestMatcher getRequestMatcher() {
		return requestMatcher;
	}

	public SelectIdentityProviderUIFilter setRequestMatcher(RequestMatcher requestMatcher) {
		this.requestMatcher = requestMatcher;
		return this;
	}

	public String getSelectTemplate() {
		return selectTemplate;
	}

	public SelectIdentityProviderUIFilter setSelectTemplate(String selectTemplate) {
		this.selectTemplate = selectTemplate;
		return this;
	}

	public boolean isRedirectOnSingleProvider() {
		return redirectOnSingleProvider;
	}

	public SelectIdentityProviderUIFilter setRedirectOnSingleProvider(boolean redirectOnSingleProvider) {
		this.redirectOnSingleProvider = redirectOnSingleProvider;
		return this;
	}

	public static class ModelProvider {
		private String linkText;
		private String redirect;

		public String getLinkText() {
			return linkText;
		}

		public ModelProvider setLinkText(String linkText) {
			this.linkText = linkText;
			return this;
		}

		public String getRedirect() {
			return redirect;
		}

		public ModelProvider setRedirect(String redirect) {
			this.redirect = redirect;
			return this;
		}
	}
}
