import { loadConfig } from './config.js';
import { createStorefrontServer } from './server.js';

const config = loadConfig(process.env);
createStorefrontServer(config).listen(config.port, () => {
  console.log(`storefront listening on :${config.port}, gateway ${config.gatewayUrl}`);
});
