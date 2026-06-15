scoreboard players add totalTimeT Core 1
execute if score totalTimeT Core matches 20.. run scoreboard players add totalTimeS Core 1
execute if score totalTimeT Core matches 20.. run scoreboard players set totalTimeT Core 0
execute if score totalTimeS Core matches 60.. run scoreboard players add totalTimeM Core 1
execute if score totalTimeS Core matches 60.. run scoreboard players set totalTimeS Core 0

scoreboard players remove TimeT Core 1
execute if score TimeT Core matches ..0 run scoreboard players remove TimeS Core 1
execute if score TimeT Core matches ..0 run scoreboard players set TimeT Core 20
execute if score TimeS Core matches ..-1 run scoreboard players remove TimeM Core 1
execute if score TimeS Core matches ..-1 run scoreboard players set TimeS Core 59

execute if score TimeM Core matches ..-1 if score randomCycle Core matches 0 run function ds:game/reset_time
execute if score TimeM Core matches ..-1 if score randomCycle Core matches 1 run function ds:game/random_cycle

execute if score showTimer Core matches 1 run title @a actionbar [{"text":">> Swap: ","color":"green"},{"score":{"name":"TimeM","objective":"Core"},"color":"gold"},{"text":":","color":"gold"},{"score":{"name":"TimeS","objective":"Core"},"color":"gold"},{"text":" <<","color":"green"}]

# == items clock
scoreboard players remove TimeT Items 1
execute if score TimeT Items matches ..0 run scoreboard players remove TimeS Items 1
execute if score TimeT Items matches ..0 run scoreboard players set TimeT Items 20

function ds:items/macro-detect_item
###execute if score TimeS Items matches 1 if score TimeT Items matches 9 as @r[tag=playing,limit=1,tag=!got_items] run function ds:items/give_items
execute if score TimeS Items matches 1 if score TimeT Items matches 9 run function ds:items/prep-give_items
execute if score TimeS Items matches ..0 run function ds:items/reset_time
execute as @a[tag=playing,scores={currItem=1..,select=1..}] run function ds:items/1trigger_select
function ds:items/active_items

# ------------------------------------------------------------------------------------------------

# ==== MID-GAME:
# tp_away:
execute as @a[tag=playing] if score @s tp_away matches 1.. run function ds:game/tp_away
##execute as @a[tag=playing,tag=cant_tp_away] run scoreboard players add @s used_tp_cycle 5
##execute as @a[tag=playing,tag=cant_tp_away] if score @s used_tp_cycle matches 48100.. run function ds:game/reset_used_tp

execute as @a[tag=playing,tag=no_death] run scoreboard players add @s no_death 5
execute as @a[tag=playing,tag=no_death] if score @s justDied matches 1.. run function ds:game/cancel_death
execute as @a[tag=playing,tag=no_death] if score @s no_death matches 800.. run function ds:game/can_die_again 

execute as @a[tag=playing,tag=!no_death] if score @s justDied matches 1.. run function ds:game/player_died
execute if score Players Core matches ..1 if score eliminations Core matches 1.. unless score someoneCrashed Core matches 1 run function ds:game/prep_winner

execute if score TimeS Core matches 10 if score TimeT Core matches 15 run effect clear @a minecraft:night_vision

# Kill excess items:
execute as @e[type=minecraft:item,nbt=!{Item:{components:{"minecraft:custom_data":{deathswapitem:true}}}}] at @s unless entity @p[tag=playing,distance=..6] if score TimeS Core matches 58 if score TimeT Core matches 19 run kill @s
execute as @e[type=minecraft:item,nbt=!{Item:{components:{"minecraft:custom_data":{deathswapitem:true}}}}] at @s unless entity @p[tag=playing,distance=..6] if score TimeS Core matches 45 if score TimeT Core matches 19 run kill @s
execute as @e[type=minecraft:item,nbt=!{Item:{components:{"minecraft:custom_data":{deathswapitem:true}}}}] at @s unless entity @p[tag=playing,distance=..6] if score TimeS Core matches 30 if score TimeT Core matches 19 run kill @s
execute as @e[type=minecraft:item,nbt=!{Item:{components:{"minecraft:custom_data":{deathswapitem:true}}}}] at @s unless entity @p[tag=playing,distance=..6] if score TimeS Core matches 20 if score TimeT Core matches 19 run kill @s
execute as @e[type=minecraft:item,nbt=!{Item:{components:{"minecraft:custom_data":{deathswapitem:true}}}}] at @s unless entity @p[tag=playing,distance=..6] if score TimeS Core matches 11 if score TimeT Core matches 19 run kill @s
execute as @e[type=minecraft:item,nbt=!{Item:{components:{"minecraft:custom_data":{deathswapitem:true}}}}] at @s unless entity @p[tag=playing,distance=..6] if score TimeS Core matches 6 if score TimeT Core matches 19 run kill @s

# start tp_away
execute if score totalTimeS Core matches 32 if score totalTimeM Core matches 0 if score totalTimeT Core matches 1 run function ds:game/start_tp_away

#execute if score totalTimeS Core matches 32 if score totalTimeM Core matches 0 if score totalTimeT Core matches 1 if score Lang Core matches 1 run tellraw @a [{"text":"*>>> PROTIP: ","color":"gold","bold":true,"italic":true},{"text":"Leave at least 3 slots in your hotbar empty, so you don't have to go searching your inventory for the items!","color":"green","bold":false}]
#execute if score totalTimeS Core matches 32 if score totalTimeM Core matches 0 if score totalTimeT Core matches 1 if score Lang Core matches 2 run tellraw @a [{"text":"*>>> PROTIP: ","color":"gold","bold":true,"italic":true},{"text":"快捷栏里至少留出 3 个空位, 这样你就不用在物品栏里翻找物品了!","color":"green","bold":false}]
#execute if score totalTimeS Core matches 32 if score totalTimeM Core matches 0 if score totalTimeT Core matches 1 as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 9
#execute if score totalTimeS Core matches 32 if score totalTimeM Core matches 0 if score totalTimeT Core matches 1 as @a at @s run playsound minecraft:block.note_block.bit master @s ~ ~ ~ 9

execute as @a if score @s ate_notch_apple matches 1.. run function ds:extra/ate_notch_apple

# ============ countdown:
execute if score TimeM Core matches 1 if score TimeS Core matches 1 if score TimeT Core matches 2 if score warnLvl Core matches 4.. run title @a title {"text":">> Swapping <<","color":"gold"}
execute if score TimeM Core matches 1 if score TimeS Core matches 1 if score TimeT Core matches 2 if score warnLvl Core matches 4.. as @a at @s run playsound minecraft:block.anvil.land master @s ~ ~ ~ 9 0
execute if score TimeM Core matches 1 if score TimeS Core matches 1 if score TimeT Core matches 2 if score warnLvl Core matches 4.. run title @a subtitle {"text":"In 1 minute","color":"yellow"}

execute if score TimeM Core matches 0 if score TimeS Core matches 31 if score TimeT Core matches 2 if score warnLvl Core matches 3.. run title @a title {"text":">> Swapping <<","color":"gold"}
execute if score TimeM Core matches 0 if score TimeS Core matches 31 if score TimeT Core matches 2 if score warnLvl Core matches 3.. as @a at @s run playsound minecraft:block.anvil.land master @s ~ ~ ~ 9 0
execute if score TimeM Core matches 0 if score TimeS Core matches 31 if score TimeT Core matches 2 if score warnLvl Core matches 3.. run title @a subtitle {"text":"In 30 seconds","color":"yellow"}

execute if score TimeM Core matches 0 if score TimeS Core matches 10 if score TimeT Core matches 19 if score warnLvl Core matches 2.. run title @a title {"text":">> Swapping <<","color":"gold"}
execute if score TimeM Core matches 0 if score TimeS Core matches 10 if score TimeT Core matches 19 if score warnLvl Core matches 2.. as @a at @s run playsound minecraft:block.anvil.land master @s ~ ~ ~ 9 0
execute if score TimeM Core matches 0 if score TimeS Core matches 10 if score TimeT Core matches 19 if score warnLvl Core matches 2.. run title @a subtitle {"text":"In 10 seconds","color":"yellow"}

execute if score TimeM Core matches 0 if score TimeS Core matches 1..5 if score TimeT Core matches 19 if score warnLvl Core matches 1.. run title @a title {"text":">> Swapping <<","color":"gold"}
execute if score TimeM Core matches 0 if score TimeS Core matches 1..5 if score TimeT Core matches 19 if score warnLvl Core matches 1.. as @a at @s run playsound minecraft:block.anvil.land master @s ~ ~ ~ 9 0
execute if score TimeM Core matches 0 if score TimeS Core matches 5 if score TimeT Core matches 19 if score warnLvl Core matches 1.. run title @a subtitle {"text":"In 5","color":"yellow"}
execute if score TimeM Core matches 0 if score TimeS Core matches 4 if score TimeT Core matches 19 if score warnLvl Core matches 1.. run title @a subtitle {"text":"In 4","color":"yellow"}
execute if score TimeM Core matches 0 if score TimeS Core matches 3 if score TimeT Core matches 19 if score warnLvl Core matches 1.. run title @a subtitle {"text":"In 3","color":"yellow"}
execute if score TimeM Core matches 0 if score TimeS Core matches 2 if score TimeT Core matches 19 if score warnLvl Core matches 1.. run title @a subtitle {"text":"In 2","color":"gold"}
execute if score TimeM Core matches 0 if score TimeS Core matches 1 if score TimeT Core matches 19 if score warnLvl Core matches 1.. run title @a subtitle {"text":"In 1","color":"red"}

# =============== SWAPPING:
execute if score TimeM Core matches 0 if score TimeS Core matches 0 if score TimeT Core matches 17 run function ds:game/reassign_pno
execute if score TimeM Core matches 0 if score TimeS Core matches 0 if score TimeT Core matches 16 run function ds:game/swap