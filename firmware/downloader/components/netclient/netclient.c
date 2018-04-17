#include "mbedtls/net.h"
#include "mbedtls/ssl.h"
#include "mbedtls/entropy.h"
#include "mbedtls/ctr_drbg.h"
#include "mbedtls/debug.h"

#include <string.h>

#include "netclient.h"

#define SERVER_ADDR "localhost"
#define SERVER_PORT "8081"

#define NETCLIENT_MBEDTLS_ERR_CHECK(name, expr) { \
    int err = expr; \
    if (err != 0) { \
      printf("Failed to %s: -0x%X\n", name, -err); \
      return err; \
    } \
  }

static void my_debug( void *ctx, int level,
                      const char *file, int line, const char *str ) {
  ((void) level);
  fprintf( (FILE *) ctx, "%s:%04d: %s", file, line, str );
  fflush(  (FILE *) ctx  );
}

int netclient_main() {
  mbedtls_net_context server_fd;
  mbedtls_entropy_context entropy;
  mbedtls_ctr_drbg_context ctr_drbg;
  mbedtls_ssl_context ssl;
  mbedtls_ssl_config conf;
  mbedtls_x509_crt ca_cert;
  mbedtls_x509_crt own_cert;
  mbedtls_pk_context own_key;

  mbedtls_net_init(&server_fd);
  mbedtls_ssl_init(&ssl);
  mbedtls_ssl_config_init(&conf);
  mbedtls_x509_crt_init(&ca_cert);
  mbedtls_x509_crt_init(&own_cert);
  mbedtls_pk_init(&own_key);
  mbedtls_ctr_drbg_init(&ctr_drbg);

  NETCLIENT_MBEDTLS_ERR_CHECK("load ca cert", mbedtls_x509_crt_parse_file(&ca_cert, "picoca.cer"));
  NETCLIENT_MBEDTLS_ERR_CHECK("load own cert", mbedtls_x509_crt_parse_file(&own_cert, "client.cer"));
  NETCLIENT_MBEDTLS_ERR_CHECK("load own key", mbedtls_pk_parse_keyfile(&own_key, "client.pkcs8", NULL));

  mbedtls_entropy_init(&entropy);
  NETCLIENT_MBEDTLS_ERR_CHECK("init entropy", mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy, NULL, 0));

  NETCLIENT_MBEDTLS_ERR_CHECK("connect", mbedtls_net_connect(&server_fd, SERVER_ADDR, SERVER_PORT, MBEDTLS_NET_PROTO_TCP));

  NETCLIENT_MBEDTLS_ERR_CHECK("init TLS",
                    mbedtls_ssl_config_defaults(&conf,
                                                MBEDTLS_SSL_IS_CLIENT,
                                                MBEDTLS_SSL_TRANSPORT_STREAM,
                                                MBEDTLS_SSL_PRESET_DEFAULT));

  //FIXME: verify server certificate
  mbedtls_ssl_conf_authmode(&conf, MBEDTLS_SSL_VERIFY_REQUIRED);
  mbedtls_ssl_conf_ca_chain(&conf, &ca_cert, NULL);
  mbedtls_ssl_conf_own_cert(&conf, &own_cert, &own_key);

  mbedtls_ssl_conf_rng(&conf, mbedtls_ctr_drbg_random, &ctr_drbg);
  mbedtls_ssl_conf_dbg(&conf, my_debug, stdout);

  NETCLIENT_MBEDTLS_ERR_CHECK("setup TLS", mbedtls_ssl_setup(&ssl, &conf));
  mbedtls_ssl_set_bio(&ssl, &server_fd, mbedtls_net_send, mbedtls_net_recv, NULL);

  printf("Starting handshake\n");
  {
    int err;
    while ((err = mbedtls_ssl_handshake(&ssl)) != 0) {
      if (err != MBEDTLS_ERR_SSL_WANT_READ && err != MBEDTLS_ERR_SSL_WANT_WRITE) {
        printf("Failed to handshake: -0x%X\n", -err);
        return err;
      }
    }
  }
  printf("Ending handshake\n");

  mbedtls_ssl_write(&ssl, "hello world", 11);

  mbedtls_net_free(&server_fd);
  mbedtls_ssl_free(&ssl);
  mbedtls_ssl_config_free(&conf);
  mbedtls_ctr_drbg_free(&ctr_drbg);
  mbedtls_entropy_free(&entropy);
  mbedtls_x509_crt_free(&ca_cert);
  mbedtls_x509_crt_free(&own_cert);
  mbedtls_pk_free(&own_key);

  return 0;
}
