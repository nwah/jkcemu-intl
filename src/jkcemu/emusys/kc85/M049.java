/*
 * (c) 2022 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation eines 512-User-PROM-Moduls M049 (32x16 KByte)
 */

package jkcemu.emusys.kc85;

import java.awt.Component;


public class M049 extends AbstractKC85UserPROMModule
{
  public M049( int slot, int typeByte, Component owner, String fileName )
  {
    super( slot, typeByte, "M049", 32, 0x4000, owner, fileName );
  }


	/* --- ueberschriebene Methoden --- */

  // Steuerbyte: AASSSSSM
  @Override
  public void setStatus( int value )
  {
    super.setStatus( value );
    this.begAddr = (value << 8) & 0xC000;
    this.segMask = (value << 13) & 0x7C000;
  }
}
