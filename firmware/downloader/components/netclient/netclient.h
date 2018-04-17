int netclient_main();

#define NETCLIENT_MBEDTLS_ERR_CHECK(name, expr) {  \
    int err = expr;                                \
    if (err != 0) {                                \
      printf("Failed to %s: -0x%X\n", name, -err); \
      return err;                                  \
    }                                              \
  }

#define NETCLIENT_MBEDTLS_ERR_RET(expr) { \
    int err = expr;                       \
    if (err != 0) {                       \
      return err;                         \
    }                                     \
  }
