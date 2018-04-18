#include "mbedtls/net.h"
#include "mbedtls/ssl.h"
#include "mbedtls/entropy.h"
#include "mbedtls/ctr_drbg.h"
#include "mbedtls/debug.h"

#ifndef __NETCLIENT_H
#define __NETCLIENT_H

int netclient_main();

#define NETCLIENT_MBEDTLS_ERR_CHECK(name, expr) {             \
    int err = expr;                                           \
    if (err < 0) {                                            \
      printf("NETCLIENT: Failed to %s: -0x%X\n", name, -err); \
      return err;                                             \
    }                                                         \
  }

#define NETCLIENT_MBEDTLS_ERR_RET(expr) { \
    int err = expr;                       \
    if (err < 0) {                        \
      return err;                         \
    }                                     \
  }

typedef struct {
  mbedtls_net_context server_fd;
  mbedtls_entropy_context entropy;
  mbedtls_ctr_drbg_context ctr_drbg;
  mbedtls_ssl_context ssl;
  mbedtls_ssl_config conf;
  mbedtls_x509_crt ca_cert;
  mbedtls_x509_crt own_cert;
  mbedtls_pk_context own_key;
} netclient_context;

void netclient_init(netclient_context *ctx);
void netclient_free(netclient_context *ctx);
int netclient_setup(netclient_context *ctx);
int netclient_connect(netclient_context *ctx);
int netclient_read(netclient_context *ctx, unsigned char *buf, int len);
int netclient_write(netclient_context *ctx, const unsigned char *msg, int len);

#endif
