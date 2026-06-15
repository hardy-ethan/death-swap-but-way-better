execute as @s run function ds:items/use/all
#execute if score Lang Core matches 1 run title @s title "Look down!"
#execute if score Lang Core matches 2 run title @s title "瞧不起!"
#execute as @s at @s run setblock ~ ~ ~ chest{Items:[{Slot:12b,id:"minecraft:elytra",count:1},{Slot:14b,id:"minecraft:firework_rocket",count:16}]} replace
give @s minecraft:elytra
give @s minecraft:firework_rocket 8

tellraw @s [{"text":"**You now have an elytra & 16 fireworks!","color":"red"}]
execute as @s at @s run playsound minecraft:entity.item.pickup master @s ~ ~ ~ 9

execute as @s run function ds:items/after_use