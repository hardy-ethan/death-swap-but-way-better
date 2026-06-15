execute as @s run function ds:items/use/all
tag @r[tag=playing,limit=1] add inv_clear
clear @a[tag=playing,tag=inv_clear]
title @a[tag=inv_clear] title " "
title @a[tag=inv_clear] subtitle [{"selector":"@s"},{"text":" cleared your inventory!","color":"red"}]
title @s title " "
title @s subtitle [{"text":"You cleared ","color":"red"},{"selector":"@p[tag=inv_clear]"},{"text":"'s inventory!","color":"red"}]
tag @a[tag=inv_clear] remove got_notch_apple

tellraw @a [{"text":">> ","color":"green"},{"selector":"@s","bold":false}," --> ",{"text":"Cleared a random person's inventory --> ","color":"red"},{"selector":"@p[tag=inv_clear]","bold":false},{"text":" was selected!","color":"red"}]
execute as @s at @s run playsound minecraft:block.stone.break master @s ~ ~ ~ 99
execute as @s at @s run playsound minecraft:block.enchantment_table.use master @s ~ ~ ~ 9

tag @a remove inv_clear
execute as @s run function ds:items/after_use