#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_system.h"
#include "esp_spi_flash.h"
#include "nvs_flash.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_event_loop.h"
#include "esp_spiffs.h"
#include "esp_intr.h"
#include "driver/uart.h"
#include "netclient.h"

#define WIFI_SSID "TestAP"
#define WIFI_PASSWORD "verysecret"

#define DOWNLOADER_QUEUE_BUF_LEN (512)

typedef struct {
  uint16_t len;
  unsigned char buf[DOWNLOADER_QUEUE_BUF_LEN];
} downloader_queue_buf;

typedef struct {
  QueueHandle_t command_queue;
  QueueHandle_t event_queue;
} downloader_queues;

typedef struct {
  QueueHandle_t queue;
  uart_port_t uart;
} task_uart_subtask_ctx;

uint32_t bigendian_decode_uint32(unsigned char *buf) {
  uint32_t out = 0;
  for (int i = 0; i < 4; ++i) {
    out <<= 8;
    out |= buf[i];
  }
  return out;
}

void bigendian_encode_uint32(unsigned char *buf, uint32_t value) {
  for (int i = 3; i >= 0; --i) {
    buf[i] = (unsigned char) value;
    value >>= 8;
  }
}

void task_uart_read(void *parameters) {
  task_uart_subtask_ctx ctx = *(task_uart_subtask_ctx *)parameters;
  free(parameters);

  downloader_queue_buf next_item;
  while (1) {
    uint8_t len_buf[4];
    if (uart_read_bytes(ctx.uart, len_buf, 4, portMAX_DELAY) != 4) {
      // Failed to read the packet length, start over...
      continue;
    }
    next_item.len = bigendian_decode_uint32(len_buf);

    if (next_item.len > DOWNLOADER_QUEUE_BUF_LEN) {
      printf("UART message too big (%d > %d), dropping it...\n", next_item.len, DOWNLOADER_QUEUE_BUF_LEN);
      continue;
    }

    int read_len = uart_read_bytes(ctx.uart, next_item.buf, next_item.len, pdMS_TO_TICKS(200));
    if (read_len != next_item.len) {
      printf("Failed to read UART message: read len %d but expected %d\n", read_len, next_item.len);
      continue;
    }

    xQueueSend(ctx.queue, &next_item, portMAX_DELAY);
  }

  vTaskDelete(NULL);
}

void task_uart_write(void *parameters) {
  task_uart_subtask_ctx ctx = *(task_uart_subtask_ctx *)parameters;
  free(parameters);

  downloader_queue_buf next_item;
  while (1) {
    xQueueReceive(ctx.queue, &next_item, portMAX_DELAY);

    unsigned char len_buf[4];
    bigendian_encode_uint32(len_buf, next_item.len);
    uart_write_bytes(ctx.uart, (const char *) len_buf, 4);
    uart_write_bytes(ctx.uart, (const char *) next_item.buf, next_item.len);
  }

  vTaskDelete(NULL);
}

typedef struct {
  QueueHandle_t queue;
  netclient_context *netclient;
} task_netclient_subtask_ctx;

void task_netclient_read(void *parameters) {
  task_netclient_subtask_ctx ctx = *(task_netclient_subtask_ctx *)parameters;
  free(parameters);

  downloader_queue_buf next_item;
  while (1) {
    unsigned char len_buf[4];
    if (netclient_read(ctx.netclient, len_buf, 4) != 4) {
      printf("Failed to read net packet length, restarting\n");
      esp_restart();
    }
    uint32_t len = bigendian_decode_uint32(len_buf);

    if (len > DOWNLOADER_QUEUE_BUF_LEN) {
      printf("Net message is too big (%d > %d), restarting\n", len, DOWNLOADER_QUEUE_BUF_LEN);
    }

    next_item.len = netclient_read(ctx.netclient, next_item.buf, len);
    if (next_item.len != len) {
      printf("Failed to read net message: read len %d but expected %d\n", next_item.len, len);
      esp_restart();
    }

    if (next_item.len > 0) {
      xQueueSend(ctx.queue, &next_item, portMAX_DELAY);
    }
  }

  vTaskDelete(NULL);
}

void task_netclient_write(void *parameters) {
  task_netclient_subtask_ctx ctx = *(task_netclient_subtask_ctx *)parameters;
  free(parameters);

  downloader_queue_buf next_item;
  while (1) {
    xQueueReceive(ctx.queue, &next_item, portMAX_DELAY);
    unsigned char len_buf[4];
    bigendian_encode_uint32(len_buf, next_item.len);
    netclient_write(ctx.netclient, len_buf, 4);
    netclient_write(ctx.netclient, next_item.buf, next_item.len);
  }

  vTaskDelete(NULL);
}

void task_netclient(void *parameters) {
  downloader_queues *queues = parameters;

  netclient_context *netclient = malloc(sizeof(netclient_context));
  netclient_init(netclient);
  printf("setupping\n");
  if (netclient_setup(netclient) != 0) {
    printf("setupping failed\n");
    esp_restart();
    vTaskDelete(NULL);
    return;
  }
  printf("setupped, waiting for IP\n");
  printf("connectifying\n");
  if (netclient_connect(netclient) != 0) {
    printf("connectifying failed\n");
    esp_restart();
    vTaskDelete(NULL);
    return;
  }
  printf("Ready to roll!\n");

  task_netclient_subtask_ctx *writer_ctx = malloc(sizeof(task_netclient_subtask_ctx));
  writer_ctx->queue = queues->event_queue;
  writer_ctx->netclient = netclient;

  task_netclient_subtask_ctx *reader_ctx = malloc(sizeof(task_netclient_subtask_ctx));
  reader_ctx->queue = queues->command_queue;
  reader_ctx->netclient = netclient;

  xTaskCreate(&task_netclient_write, "task_netclient_write", 16384, writer_ctx, 1, NULL);
  xTaskCreate(&task_netclient_read, "task_netclient_read", 16384, reader_ctx, 1, NULL);

  vTaskDelete(NULL);
}

esp_err_t event_handler(void *ctx, system_event_t *event) {
  downloader_queues *queues = ctx;
  switch(event->event_id) {
  case SYSTEM_EVENT_STA_GOT_IP:
    printf("Got IP!\n");
    xTaskCreate(&task_netclient, "task_netclient", 16384, queues, 1, NULL);
    break;
  default:
    break;
  }
  return ESP_OK;
}

void app_main() {
    printf("Hello world!\n");
    ESP_ERROR_CHECK(nvs_flash_init());
    tcpip_adapter_init();

    downloader_queues *queues = malloc(sizeof(downloader_queues));
    queues->command_queue = xQueueCreate(1, sizeof(downloader_queue_buf));
    queues->event_queue = xQueueCreate(1, sizeof(downloader_queue_buf));

    uart_config_t uart_config = {
      .baud_rate = 115200,
      .data_bits = UART_DATA_8_BITS,
      .parity = UART_PARITY_DISABLE,
      .stop_bits = UART_STOP_BITS_1,
      .flow_ctrl = UART_HW_FLOWCTRL_DISABLE,
      .rx_flow_ctrl_thresh = 0,
      .use_ref_tick = 0
    };
    ESP_ERROR_CHECK(uart_param_config(UART_NUM_1, &uart_config));
    ESP_ERROR_CHECK(uart_set_pin(UART_NUM_1,
                                 17,                   // TX
                                 16,                   // RX
                                 UART_PIN_NO_CHANGE,   // RTS
                                 UART_PIN_NO_CHANGE)); // CTS
    ESP_ERROR_CHECK(uart_driver_install(UART_NUM_1,
                                        1024, // RX buffer
                                        1024, // TX buffer
                                        0,    // event queue depth
                                        NULL, // event queue
                                        ESP_INTR_FLAG_LOWMED
                                        ));

    task_uart_subtask_ctx *uart_writer_ctx = malloc(sizeof(task_uart_subtask_ctx));
    uart_writer_ctx->queue = queues->command_queue;
    uart_writer_ctx->uart = UART_NUM_1;

    task_uart_subtask_ctx *uart_reader_ctx = malloc(sizeof(task_uart_subtask_ctx));
    uart_reader_ctx->queue = queues->event_queue;
    uart_reader_ctx->uart = UART_NUM_1;

    xTaskCreate(&task_uart_write, "task_uart_write", 16384, uart_writer_ctx, 1, NULL);
    xTaskCreate(&task_uart_read, "task_uart_read", 16384, uart_reader_ctx, 1, NULL);

    ESP_ERROR_CHECK(gpio_set_direction(GPIO_NUM_21, GPIO_MODE_OUTPUT));
    ESP_ERROR_CHECK(gpio_set_level(GPIO_NUM_21, 1));

    ESP_ERROR_CHECK(esp_event_loop_init(event_handler, queues));

    esp_vfs_spiffs_conf_t spiffs = {
      .base_path = "/config",
      .partition_label = "config",
      .max_files = 10,
      .format_if_mount_failed = false
    };
    ESP_ERROR_CHECK(esp_vfs_spiffs_register(&spiffs));

    wifi_init_config_t config = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&config));
    ESP_ERROR_CHECK(esp_wifi_set_storage(WIFI_STORAGE_RAM));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));

    wifi_config_t sta_config = {
      .sta = {
        .ssid = WIFI_SSID,
        .password = WIFI_PASSWORD,
        .bssid_set = false
      }
    };
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &sta_config));
    ESP_ERROR_CHECK(esp_wifi_start());
    printf("Connecting...\n");
    ESP_ERROR_CHECK(esp_wifi_connect());
}
