package io.jenkins.plugins.rancher2;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class Rancher2CredentialsImpl extends BaseStandardCredentials implements Rancher2Credentials {
    private String endpoint;
    private boolean trustCert;
    private final String bearerToken;

    @DataBoundConstructor
    public Rancher2CredentialsImpl(
            @CheckForNull CredentialsScope scope,
            @CheckForNull String id,
            @NonNull String endpoint,
            boolean trustCert,
            @NonNull String bearerToken,
            String description) {
        super(scope, id, description);
        this.endpoint = endpoint;
        this.trustCert = trustCert;
        this.bearerToken = bearerToken;
    }

    @Override
    public String getEndpoint() throws IOException, InterruptedException {
        return endpoint;
    }

    @Override
    public boolean isTrustCert() throws IOException, InterruptedException {
        return trustCert;
    }

    @Override
    public String getBearerToken() throws IOException, InterruptedException {
        return bearerToken;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.Rancher2CredentialsImpl_DescriptorImpl_displayName();
        }

        public FormValidation doCheckEndpoint(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_endpointIsEmpty());
            }
            if (value.endsWith("/")) {
                return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_endpointRedundantSuffix());
            }
            if (!value.endsWith("/v3")) {
                return FormValidation.warning(Messages.Rancher2CredentialsImpl_DescriptorImpl_endpointNotV3());
            }
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                return FormValidation.warning(Messages.Rancher2CredentialsImpl_DescriptorImpl_endpointNotHttp());
            }
            try {
                new URL(value);
                return FormValidation.ok();
            } catch (MalformedURLException e) {
                return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_endpointNotURL());
            }
        }

        public FormValidation doCheckBearerToken(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_tokenIsEmpty());
            }
            if (value.startsWith("Bearer")) {
                return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_tokenRedundantPrefix());
            }
            return FormValidation.ok();
        }

        public FormValidation doTestConnection(
                @QueryParameter("endpoint") final String endpoint,
                @QueryParameter("trustCert") boolean trustCert,
                @QueryParameter("bearerToken") final String bearerToken) throws IOException, ServletException {

            FormValidation validation = doCheckEndpoint(endpoint);
            if (validation.kind == FormValidation.Kind.ERROR) {
                return validation;
            }
            validation = doCheckBearerToken(bearerToken);
            if (validation.kind == FormValidation.Kind.ERROR) {
                return validation;
            }

            try (CloseableHttpClient client = ClientBuilder.create(endpoint, trustCert)) {
                RequestBuilder requestBuilder = RequestBuilder.get(endpoint + (endpoint.endsWith("/") ? "projects" : "/projects"));
                requestBuilder.addHeader("Authorization", "Bearer " + bearerToken);
                HttpUriRequest request = requestBuilder.build();
                CloseableHttpResponse response = client.execute(request);
                if (response.getStatusLine().getStatusCode() == 200) {
                    return FormValidation.ok(Messages.Rancher2CredentialsImpl_DescriptorImpl_connectSucceed());
                }
                if (response.getStatusLine().getStatusCode() == 401) {
                    return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_badTokenScope());
                }
                String body = EntityUtils.toString(response.getEntity());
                return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_badResponse(
                        response.getStatusLine().getStatusCode(),
                        body
                ));
            } catch (Exception e) {
                return FormValidation.error(Messages.Rancher2CredentialsImpl_DescriptorImpl_testError(e.getMessage()));
            }
        }
    }
}
