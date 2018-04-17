#include <string.h>

#include "netclient.h"

#define SERVER_ADDR "localhost"
#define SERVER_PORT "8081"

static void my_debug( void *ctx, int level,
                      const char *file, int line, const char *str ) {
  ((void) level);
  fprintf( (FILE *) ctx, "NETCLIENT: %s:%04d: %s", file, line, str );
  fflush(  (FILE *) ctx  );
}

void netclient_init(netclient_context *ctx) {
  mbedtls_net_init(&ctx->server_fd);
  mbedtls_entropy_init(&ctx->entropy);
  mbedtls_ctr_drbg_init(&ctx->ctr_drbg);
  mbedtls_ssl_init(&ctx->ssl);
  mbedtls_ssl_config_init(&ctx->conf);
  mbedtls_x509_crt_init(&ctx->ca_cert);
  mbedtls_x509_crt_init(&ctx->own_cert);
  mbedtls_pk_init(&ctx->own_key);
}

void netclient_free(netclient_context *ctx) {
  mbedtls_pk_free(&ctx->own_key);
  mbedtls_x509_crt_free(&ctx->own_cert);
  mbedtls_x509_crt_free(&ctx->ca_cert);
  mbedtls_ssl_config_free(&ctx->conf);
  mbedtls_ssl_free(&ctx->ssl);
  mbedtls_ctr_drbg_free(&ctx->ctr_drbg);
  mbedtls_entropy_free(&ctx->entropy);
  mbedtls_net_free(&ctx->server_fd);
}

int netclient_load_keys(netclient_context *ctx) {
  NETCLIENT_MBEDTLS_ERR_CHECK("load ca cert", mbedtls_x509_crt_parse_file(&ctx->ca_cert, "picoca.cer"));
  NETCLIENT_MBEDTLS_ERR_CHECK("load own cert", mbedtls_x509_crt_parse_file(&ctx->own_cert, "client.cer"));
  NETCLIENT_MBEDTLS_ERR_CHECK("load own key", mbedtls_pk_parse_keyfile(&ctx->own_key, "client.pkcs8", NULL));

  mbedtls_ssl_conf_authmode(&ctx->conf, MBEDTLS_SSL_VERIFY_REQUIRED);
  mbedtls_ssl_conf_ca_chain(&ctx->conf, &ctx->ca_cert, NULL);
  mbedtls_ssl_conf_own_cert(&ctx->conf, &ctx->own_cert, &ctx->own_key);

  return 0;
}

int netclient_seed_rng(netclient_context *ctx) {
  NETCLIENT_MBEDTLS_ERR_CHECK("init entropy", mbedtls_ctr_drbg_seed(&ctx->ctr_drbg, mbedtls_entropy_func, &ctx->entropy, NULL, 0));
  mbedtls_ssl_conf_rng(&ctx->conf, mbedtls_ctr_drbg_random, &ctx->ctr_drbg);
  return 0;
}

void netclient_setup_debug(netclient_context *ctx) {
  mbedtls_ssl_conf_dbg(&ctx->conf, my_debug, stdout);
}

int netclient_connect(netclient_context *ctx) {
  NETCLIENT_MBEDTLS_ERR_CHECK("connect", mbedtls_net_connect(&ctx->server_fd, SERVER_ADDR, SERVER_PORT, MBEDTLS_NET_PROTO_TCP));

  NETCLIENT_MBEDTLS_ERR_CHECK("init TLS",
                              mbedtls_ssl_config_defaults(&ctx->conf,
                                                          MBEDTLS_SSL_IS_CLIENT,
                                                          MBEDTLS_SSL_TRANSPORT_STREAM,
                                                          MBEDTLS_SSL_PRESET_DEFAULT));


  NETCLIENT_MBEDTLS_ERR_CHECK("setup TLS", mbedtls_ssl_setup(&ctx->ssl, &ctx->conf));
  mbedtls_ssl_set_bio(&ctx->ssl, &ctx->server_fd, mbedtls_net_send, mbedtls_net_recv, NULL);

  printf("NETCLIENT: Starting handshake\n");
  {
    int err;
    while ((err = mbedtls_ssl_handshake(&ctx->ssl)) != 0) {
      if (err != MBEDTLS_ERR_SSL_WANT_READ && err != MBEDTLS_ERR_SSL_WANT_WRITE) {
        printf("NETCLIENT: Failed to handshake: -0x%X\n", -err);
        return err;
      }
    }
  }
  printf("NETCLIENT: Ending handshake\n");
  printf("NETCLIENT: Sending HELLO\n");
  unsigned char hello_msg[] = {
    0, 0, 0, 0
  };
  NETCLIENT_MBEDTLS_ERR_RET(netclient_write(ctx, hello_msg, sizeof(hello_msg)));
  printf("NETCLIENT: HELLO done\n");

  return 0;
}

int netclient_setup(netclient_context *ctx) {
  netclient_setup_debug(ctx);
  NETCLIENT_MBEDTLS_ERR_RET(netclient_load_keys(ctx));
  NETCLIENT_MBEDTLS_ERR_RET(netclient_seed_rng(ctx));
  return 0;
}

int netclient_write(netclient_context *ctx, const unsigned char *msg, int len) {
  while (1) {
    int ret = mbedtls_ssl_write(&ctx->ssl, msg, len);
    if (ret >= len) {
      return 0;
    } else if (ret >= 0) {
      msg += ret;
      len -= ret;
    } else if (ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE) {
      NETCLIENT_MBEDTLS_ERR_CHECK("write", ret);
    }
  }
  return 0;
}

int netclient_main() {
  netclient_context ctx;
  netclient_init(&ctx);
  NETCLIENT_MBEDTLS_ERR_RET(netclient_setup(&ctx));
  NETCLIENT_MBEDTLS_ERR_RET(netclient_connect(&ctx));

  netclient_free(&ctx);
  return 0;
}
