export const environment = {
  production: false,
  // Local API (LocalServer.java default port). Terraform overwrites this file's
  // apiBaseUrl with the Function URL output at deploy time (see infrastructure.spec.md).
  apiBaseUrl: 'http://localhost:8080',
};
