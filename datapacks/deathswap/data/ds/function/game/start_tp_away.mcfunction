execute as @a[tag=playing] run scoreboard players enable @s tp_away

execute if score Lang Core matches 1 run tellraw @a [{"text":">> ","color":"dark_purple","italic":true},{"text":"IMPORTANT: ","color":"red"},{"text":"ONCE a game, you can type ''","color":"light_purple"},{"text":"/trigger tp_away","color":"green","italic":false},{"text":"'' if the area you're in gets too laggy for the server! ","color":"light_purple"},{"text":"Remember this!!","color":"yellow"}]

execute if score Lang Core matches 2 run tellraw @a [{"text":">> ","color":"dark_purple","italic":true},{"text":"非常重要: ","color":"red"},{"text":"如果你所在区域对服务器来说太卡顿，你可以在每局游戏中输入一次: ''","color":"light_purple"},{"text":"/trigger tp_away","color":"green","italic":false},{"text":"''命令! ","color":"light_purple"},{"text":"记住这一点!!","color":"yellow"}]

execute as @a at @s run playsound minecraft:block.note_block.pling master @s ~ ~ ~ 9
execute as @a at @s run playsound minecraft:block.note_block.bit master @s ~ ~ ~ 9