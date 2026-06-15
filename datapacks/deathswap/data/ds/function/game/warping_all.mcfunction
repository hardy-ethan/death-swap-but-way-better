execute as @a at @s run attribute @s minecraft:movement_speed base set 0.0
execute as @a at @s run attribute @s minecraft:jump_strength base set 0.0
execute if score Lang Core matches 1 run title @a actionbar {"text":">> Spreading players... <<","color":"gold"}
execute if score Lang Core matches 2 run title @a actionbar {"text":">> 球员搬迁... <<","color":"gold"}

execute if score maxLives Core matches 1 run scoreboard players set @a[tag=playing] Lives 1
execute if score maxLives Core matches 2 run scoreboard players set @a[tag=playing] Lives 2
execute if score maxLives Core matches 3 run scoreboard players set @a[tag=playing] Lives 3
execute if score maxLives Core matches 4 run scoreboard players set @a[tag=playing] Lives 4
execute if score maxLives Core matches 5 run scoreboard players set @a[tag=playing] Lives 5
execute if score maxLives Core matches 6 run scoreboard players set @a[tag=playing] Lives 6

scoreboard players set settingsOn Core 0
##function ds:extra/setworldspawn
##function ds:extra/setworldspawn
##function ds:extra/setworldspawn
gamerule respawn_radius 1
setworldspawn -47 107 -32 90 1
####schedule function ds:game/spreadplayers 5t

schedule function ds:prep/spread/p1 2t
schedule function ds:prep/spread/p2 4t
schedule function ds:prep/spread/p3 6t
schedule function ds:prep/spread/p4 8t
schedule function ds:prep/spread/p5 10t
schedule function ds:prep/spread/p6 12t
schedule function ds:prep/spread/p7 14t
schedule function ds:prep/spread/p8 16t
schedule function ds:prep/spread/p9 18t
schedule function ds:prep/spread/p10 20t
schedule function ds:prep/spread/p11 22t
schedule function ds:prep/spread/p12 24t

scoreboard objectives setdisplay sidebar

execute if score randomCycle Core matches 1 run function ds:game/random_cycle

schedule function ds:game/game_start 5s