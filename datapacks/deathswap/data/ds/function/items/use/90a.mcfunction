execute as @s run function ds:items/use/all
scoreboard players set lowGrav Items 18200
title @a title " "
title @a subtitle {"text":"Low-gravity: 3 minutes","color":"light_purple"}
execute as @a run attribute @s minecraft:gravity base set 0.008
execute as @a run attribute @s minecraft:fall_damage_multiplier base set 0.0

execute if score Lang Core matches 1 run tellraw @a [{"text":">> ","color":"green"},{"selector":"@s","bold":false}," --> ",{"text":"Turned the game into a low-gravity environment for the next 3 minutes","color":"light_purple"}]
execute as @s at @s run playsound minecraft:block.stone.break master @s ~ ~ ~ 99
execute as @s at @s run playsound minecraft:block.enchantment_table.use master @s ~ ~ ~ 9

execute as @s run function ds:items/after_use