$execute as @s in minecraft:the_nether run tp @s $(x) 64.5 $(z)
tag @s add 106tp_guard

execute as @s at @s run playsound minecraft:block.portal.travel master @s ~ ~ ~ 9

execute as @s at @s run fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:netherrack
execute as @s at @s run fill ~-1 ~ ~-1 ~1 ~3 ~1 minecraft:light[level=10]

schedule function ds:items/misc/106tp_guard 2t