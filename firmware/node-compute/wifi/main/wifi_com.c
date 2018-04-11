/* Simple WiFi Example

   This example code is in the Public Domain (or CC0 licensed, at your option.)

   Unless required by applicable law or agreed to in writing, this
   software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
   CONDITIONS OF ANY KIND, either express or implied.
*/
#include <string.h>
#include "freertos/FreeRTOS.h" //
#include "freertos/task.h"
#include "freertos/event_groups.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "esp_event_loop.h"
#include "esp_log.h"
#include "nvs_flash.h"

#include "lwip/err.h" /*file is part of the lwIP IP/TCP stack and contains def for error const*/
#include "lwip/sys.h" /*part of lwIP IP/TCP stack and contains prototyp func for mutex, semaphores, time and mailbox*/

/* The examples use simple WiFi configuration that you can set via
   'make menuconfig'.

   If you'd rather not, just change the below entries to strings with
   the config you want - ie #define EXAMPLE_WIFI_SSID "mywifissid"
*/
#define EXAMPLE_ESP_WIFI_MODE_AP   CONFIG_ESP_WIFI_MODE_AP //TRUE:AP FALSE:STA
#define EXAMPLE_ESP_WIFI_SSID      CONFIG_ESP_WIFI_SSID
#define EXAMPLE_ESP_WIFI_PASS      CONFIG_ESP_WIFI_PASSWORD
#define EXAMPLE_MAX_STA_CONN       CONFIG_MAX_STA_CONN

/* FreeRTOS event group to signal when we are connected*/
static EventGroupHandle_t wifi_event_group;

/* The event group allows multiple bits for each event,
   but we only care about one event - are we connected
   to the AP with an IP? */
const int WIFI_CONNECTED_BIT = BIT0;

static const char *TAG = "simple wifi";

static esp_err_t event_handler(void *ctx, system_event_t *event)
{
    switch(event->event_id) {
    case SYSTEM_EVENT_STA_START:
        esp_wifi_connect();
        break;
    case SYSTEM_EVENT_STA_GOT_IP:
        ESP_LOGI(TAG, "got ip:%s",
                 ip4addr_ntoa(&event->event_info.got_ip.ip_info.ip));
        xEventGroupSetBits(wifi_event_group, WIFI_CONNECTED_BIT);
        break;
    case SYSTEM_EVENT_AP_STACONNECTED:
        ESP_LOGI(TAG, "station:"MACSTR" join, AID=%d",
                 MAC2STR(event->event_info.sta_connected.mac),
                 event->event_info.sta_connected.aid);
        break;
    case SYSTEM_EVENT_AP_STADISCONNECTED:
        ESP_LOGI(TAG, "station:"MACSTR"leave, AID=%d",
                 MAC2STR(event->event_info.sta_disconnected.mac),
                 event->event_info.sta_disconnected.aid);
        break;
    case SYSTEM_EVENT_STA_DISCONNECTED:
        esp_wifi_connect();
        xEventGroupClearBits(wifi_event_group, WIFI_CONNECTED_BIT);
        break;
    default:
        break;
    }
    return ESP_OK;
}

void wifi_init_softap()
{
    wifi_event_group = xEventGroupCreate();

    tcpip_adapter_init(); /*Initialize tcip adapter and TCIP stack inside*/
    ESP_ERROR_CHECK(esp_event_loop_init(event_handler, NULL)); 

	/* WIFI_INIT_CONFIG_DEFAULT is defined in esp_wifi.h*/
    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT(); 
	/*Initiate wifi, alloc recourses for wifi driver(control stucture, RX/TX buffer, NVS structure). This wifi start the wifi task and need to be called before 
	all others wifi API can be called. */
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
    wifi_config_t wifi_config = {
        .ap = {
            .ssid = EXAMPLE_ESP_WIFI_SSID,
            .ssid_len = strlen(EXAMPLE_ESP_WIFI_SSID),
            .password = EXAMPLE_ESP_WIFI_PASS,
            .max_connection = EXAMPLE_MAX_STA_CONN,
            .authmode = WIFI_AUTH_WPA_WPA2_PSK
        },
    };
	/* Checking if it needs a password, if the pass == 0 then the wifi authmode is open*/
    if (strlen(EXAMPLE_ESP_WIFI_PASS) == 0) { 
        wifi_config.ap.authmode = WIFI_AUTH_OPEN; 
    }
	/*Set the wifi operating mode to soft-AP, station or both. Default is soft-AP*/
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));  
	/*Set the config of the ESP32 STA or AP. The API can only be called if specified interface is enabled, for station config look at the 
	esp_wifi.h file. ESP32 is limited to only one channel*/
    ESP_ERROR_CHECK(esp_wifi_set_config(ESP_IF_WIFI_AP, &wifi_config)); 
    ESP_ERROR_CHECK(esp_wifi_start()); 
	/*Macro to output log at ESP_LOG_ERROR level */
    ESP_LOGI(TAG, "wifi_init_softap finished.SSID:%s password:%s", 				
             EXAMPLE_ESP_WIFI_SSID, EXAMPLE_ESP_WIFI_PASS);
}

void wifi_init_sta()
{
    wifi_event_group = xEventGroupCreate();

    tcpip_adapter_init();
    ESP_ERROR_CHECK(esp_event_loop_init(event_handler, NULL) );

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
	/*wifi_config_t is a typedef union with wifi_ap_config_t ap or wifi_sta_config_t sta. Here defines as a sta that is a struct with SSID of target AP,
	password of target AP, wifi_scan_method_t scan_method, wether ser MAC adress of target AP or not, MAC adress of target AP, channel of target AP, listen interval,
	wifi_sort_method_t sort_method, wifi_fast_scan_threshold_t  threshold*/
    wifi_config_t wifi_config = {
        .sta = {
            .ssid = EXAMPLE_ESP_WIFI_SSID,
            .password = EXAMPLE_ESP_WIFI_PASS
        },
    };

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA) );
    ESP_ERROR_CHECK(esp_wifi_set_config(ESP_IF_WIFI_STA, &wifi_config) );
    ESP_ERROR_CHECK(esp_wifi_start() );

    ESP_LOGI(TAG, "wifi_init_sta finished.");
    ESP_LOGI(TAG, "connect to ap SSID:%s password:%s",
             EXAMPLE_ESP_WIFI_SSID, EXAMPLE_ESP_WIFI_PASS);
}

void app_main()
{
    //Initialize NVS /*nvs_flash_init initilize the default NVS patrition and returns ESP_OK if the storage was sucsesfully initialized*/
    esp_err_t ret = nvs_flash_init(); 
	/*if ret returns no free pages then the partition get erased and error checked then called again and saved in ret*/
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES) { /*Checks if the storage contains no empty pages, may happen if the NVS partition was truncated*/
      ESP_ERROR_CHECK(nvs_flash_erase()); /*nvs_flash_erase erase all content of default NVS partition, returns ESP_OK or ESP_ERR_NOT_FOUND*/
      ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);
    
#if EXAMPLE_ESP_WIFI_MODE_AP
    ESP_LOGI(TAG, "ESP_WIFI_MODE_AP");
    wifi_init_softap();
#else
    ESP_LOGI(TAG, "ESP_WIFI_MODE_STA");
    wifi_init_sta();
#endif /*EXAMPLE_ESP_WIFI_MODE_AP*/

}
