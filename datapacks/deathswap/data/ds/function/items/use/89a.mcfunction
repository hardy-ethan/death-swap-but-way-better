execute as @s run function ds:items/use/all
tag @s add mine_faster
scoreboard players set @s mine_faster 12100
execute as @s run attribute @s minecraft:block_break_speed base set 4
title @s title " "
execute if score Lang Core matches 1 run title @s subtitle {"text":"3x Faster mining: 120 seconds","color":"aqua"}
execute if score Lang Core matches 2 run title @s subtitle {"text":"挖矿速度提升 3 倍：120 秒","color":"aqua"}

effect clear @a minecraft:night_vision

execute if score Lang Core matches 1 run tellraw @s {"text":">> You can mine blocks 3x faster for the next 120 seconds! <<","color":"aqua","bold":true}
execute if score Lang Core matches 2 run tellraw @s {"text":">> 接下来的90秒内, 你的挖矿速度可以提升3倍! <<","color":"aqua","bold":true}
execute as @s at @s run playsound minecraft:block.stone.break master @s ~ ~ ~ 99
execute as @s at @s run playsound minecraft:block.enchantment_table.use master @s ~ ~ ~ 9

execute as @s run function ds:items/after_use