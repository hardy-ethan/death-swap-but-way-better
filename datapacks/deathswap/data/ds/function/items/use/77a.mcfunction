execute as @s run function ds:items/use/all
execute if score Lang Core matches 1 run title @s title "Look down!"
execute if score Lang Core matches 2 run title @s title "瞧不起!"

execute as @s at @s run setblock ~ ~ ~ chest{Items:[{Slot:0b,id:"minecraft:oak_planks",count:8},{Slot:1b,id:"minecraft:stone",count:8},{Slot:2b,id:"minecraft:coal",count:8},{Slot:12b,id:"minecraft:crafting_table",count:1},{Slot:13b,id:"minecraft:furnace",count:1},{Slot:14b,id:"minecraft:blast_furnace",count:1}]} replace

tellraw @s [{"text":"You have a spare crafting table + furnace + materials!","color":"gold"}]
execute as @s at @s run playsound minecraft:entity.item.pickup master @s ~ ~ ~ 9

execute as @s run function ds:items/after_use
