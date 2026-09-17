/*
 * (c) 2011-2017 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation eines 16K-User-PROM-Moduls M028
 */

package jkcemu.emusys.kc85;

import java.awt.Component;
import jkcemu.lang.LangUtil;


public class M028 extends AbstractKC85UserPROMModule
{
  public M028( int slot, int typeByte, Component owner, String fileName )
  {
    super(
		slot,
		typeByte,
		LangUtil.getText( "kc85.title.m028" ),
		1,
		0x4000,
		owner,
		fileName );
  }


	/* --- ueberschriebene Methoden --- */

  // Steuerbyte: AAxxxxxM
  @Override
  public void setStatus( int value )
  {
    super.setStatus( value );
    this.begAddr = (value << 8) & 0xC000;
  }
}
