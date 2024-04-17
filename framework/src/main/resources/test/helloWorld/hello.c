// #include <stdio.h>
#include <stdint.h>
#include <riscv-pk/encoding.h>
#include "include/platform.h"
// #include "kprintf.h"
#define REG32(p, i)	((p)[(i) >> 2])

static volatile uint32_t * const gpio = (void *)(GPIO_CTRL_ADDR);

int main(void) {
  uint32_t gpio_val = 0x0000aaaa;
  REG32(gpio, GPIO_OUTPUT_EN) = 0x0000ffff;
  REG32(gpio, GPIO_OUTPUT_VAL) = 0x0000aaaa;

  while (1) {
    for (int i = 0; i < 100000; i = i + 1);
    gpio_val = gpio_val ^ 0x0000ffff;
    REG32(gpio, GPIO_OUTPUT_VAL) = gpio_val;
  }
  return 0;
}
