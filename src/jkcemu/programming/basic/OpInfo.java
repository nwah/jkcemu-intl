/*
 * (c) 2017-2025 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Informationen ueber eine Operation
 */

package jkcemu.programming.basic;


public class OpInfo
{
  private String               operator;
  private String               asmCodeI2;
  private String               asmCodeI4;
  private String               asmCodeD6;
  private String               asmCodeF4;
  private BasicLibrary.LibItem libItemI2;
  private BasicLibrary.LibItem libItemI4;
  private BasicLibrary.LibItem libItemD6;
  private BasicLibrary.LibItem libItemF4;
  private boolean              commutative;


  public OpInfo(
		String               operator,
		String               asmCodeI2,
		BasicLibrary.LibItem libItemI2,
		String               asmCodeI4,
		BasicLibrary.LibItem libItemI4,
		String               asmCodeD6,
		BasicLibrary.LibItem libItemD6,
		String               asmCodeF4,
		BasicLibrary.LibItem libItemF4,
		boolean              commutative )
  {
    this.operator    = operator;
    this.asmCodeI2   = asmCodeI2;
    this.asmCodeI4   = asmCodeI4;
    this.asmCodeD6   = asmCodeD6;
    this.asmCodeF4   = asmCodeF4;
    this.libItemI2   = libItemI2;
    this.libItemI4   = libItemI4;
    this.libItemD6   = libItemD6;
    this.libItemF4   = libItemF4;
    this.commutative = commutative;
  }


  public String getAsmCodeD6()
  {
    return this.asmCodeD6;
  }


  public String getAsmCodeF4()
  {
    return this.asmCodeF4;
  }


  public String getAsmCodeI2()
  {
    return this.asmCodeI2;
  }


  public String getAsmCodeI4()
  {
    return this.asmCodeI4;
  }


  public BasicLibrary.LibItem getLibItemD6()
  {
    return this.libItemD6;
  }


  public BasicLibrary.LibItem getLibItemF4()
  {
    return this.libItemF4;
  }


  public BasicLibrary.LibItem getLibItemI2()
  {
    return this.libItemI2;
  }


  public BasicLibrary.LibItem getLibItemI4()
  {
    return this.libItemI4;
  }


  public String getOperator()
  {
    return this.operator;
  }


  public boolean isCommutative()
  {
    return this.commutative;
  }
}
