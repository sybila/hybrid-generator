:? stayOffOn = ! EF ! (mode == offOn)
:? offOff_toOffOn = (mode == offOff EU (mode == offOn && EF stayOffOn))
:? onOff_toOffOff = (mode == onOff EU (mode == offOff && offOff_toOffOn))
:? onOn_toOnOff = ((mode == onOn && RP < 1.0 && C_1 > 1.0) EU (mode == onOff && onOff_toOffOff))