/*
 * (c) 2026 Noah Burney
 *
 * Kleincomputer-Emulator
 *
 * Emulation des FujiNet-Moduls
 *
 * Das Modul stellt einen 8K-ROM mit dem Strukturbyte 0xFB bereit.
 * Der ROM enthaelt gegenwaertig nur den Menueeintrag "FUJINET",
 * der sofort zurueckspringt (RET).
 */

package jkcemu.emusys.kc85;

import jkcemu.base.EmuThread;


public class FujiNet extends KC85ROM8KModule
{
  public static final String MODULE_NAME = "FUJINET";
  public static final String DESCRIPTION = "FujiNet";

  private static final String ROM_RESOURCE = "/rom/kc85/fujinet.bin";


  public FujiNet( int slot, EmuThread emuThread )
  {
    super( slot, emuThread, MODULE_NAME, ROM_RESOURCE );
  }
}
